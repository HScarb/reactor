package com.cnscarb.reactor.reactor;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 多线程 Handler，IO 的 read 和 write 操作仍由 Reactor 线程处理，业务处理逻辑（decode、process、encode）由该线程池处理
 */
public class MultiThreadNioHandler extends NioHandler {
    static Executor pool = Executors.newFixedThreadPool(4);

    static final int PROCESSING = 3;

    public MultiThreadNioHandler(Selector selector, SocketChannel socket) throws IOException {
        super(selector, socket);
    }

    /**
     * 重写 read 方法，从客户端 socket 读取数据之后交给线程池进行处理，而不是在当前线程直接处理
     */
    @Override
    protected synchronized void read() throws IOException {
        input.clear();
        int n = socket.read(input);
        // 判断是否读取完毕（客户端是否输入换行符）
        if (inputIsComplete(n)) {
            // 切换成处理中状态，多线程进行处理
            state = PROCESSING;
            pool.execute(new Processor());
        }
    }

    /**
     * 业务处理逻辑，处理完后切换成发送状态
     */
    synchronized void processAndHandOff() {
        try {
            // 进行业务处理
            process();
        } catch (EOFException e) {
            // 直接关闭连接
            try {
                selectionKey.channel().close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return;
        }
        // 业务处理完成，切换成发送状态。发送仍交给 Reactor 线程处理。
        state = SENDING;
        selectionKey.interestOps(SelectionKey.OP_WRITE);

        // 立即唤醒 selector，以便新注册的 OP_WRITE 事件能立即被响应。
        // 此时 Reactor 会收到并分发 OP_WRITE 事件，又会走到 Handler 的 run 方法，由 Reactor 线程继续执行 send()
        selectionKey.selector().wakeup();
    }

    class Processor implements Runnable {
        @Override
        public void run() {
            processAndHandOff();
        }
    }
}
