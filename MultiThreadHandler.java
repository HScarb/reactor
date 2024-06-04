import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 多线程基本处理器，I/O 的读写由 Reactor 线程处理，业务的处理交给线程池
 *
 * @author tongwu.net
 * @see Reactor
 * @see BasicHandler
 */
public class MultiThreadHandler extends BasicHandler {

    static Executor pool = Executors.newFixedThreadPool(4);

    static final int PROCESSING = 3;

    public MultiThreadHandler(Selector sel, SocketChannel sc) throws IOException {
        super(sel, sc);
    }

    /**
     * 读取数据，读取完毕后交给线程池处理
     * synchronized 是为了防止 MultiReactor 下多线程读 socket、多献策修改 state、多线程向线程池提交任务
     *
     * @throws IOException
     */
    @Override
    public synchronized void read() throws IOException {
        input.clear();
        int n = socket.read(input);
        if (inputIsComplete(n)) {
            // 读取完毕后将后续的处理交给线程池处理
            state = PROCESSING;
            pool.execute(new Processor());
        }
    }

    /**
     * 执行 process 逻辑，之后再让 Reactor 线程发送响应
     */
    private synchronized void processAndHandOff() {
        try {
            System.out.println("process in thread " + Thread.currentThread().getName());
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
        // 发送响应还是交给 Reactor 线程处理
        state = SENDING;
        selectionKey.interestOps(SelectionKey.OP_WRITE);

        // 立即唤醒 selector，以便新注册的 OP_WRITE 事件能被立即响应
        selectionKey.selector().wakeup();
    }

    class Processor implements Runnable {
        @Override
        public void run() {
            processAndHandOff();
        }
    }
}
