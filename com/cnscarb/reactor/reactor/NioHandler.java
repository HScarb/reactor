package com.cnscarb.reactor.reactor;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 单线程非阻塞处理器
 */
public class NioHandler implements Runnable {

    private static final int MAX_INPUT_BUFFER_SIZE = 1024;

    private static final int MAX_OUTPUT_BUFFER_SIZE = 1024;

    final SocketChannel socket;

    final SelectionKey selectionKey;

    ByteBuffer input = ByteBuffer.allocate(MAX_INPUT_BUFFER_SIZE);

    ByteBuffer output = ByteBuffer.allocate(MAX_OUTPUT_BUFFER_SIZE);

    static final int READING = 0, SENDING = 1, CLOSED = 2;

    /**
     * Handler 当前处理状态
     */
    int state = READING;

    /**
     * 缓存每次读取的内容
     */
    StringBuilder inputStringBuilder = new StringBuilder();

    public NioHandler(Selector selector, SocketChannel socket) throws IOException {
        this.socket = socket;
        // 设置非阻塞（NIO）。这样，socket 上的操作如果无法立即完成，不会阻塞，而是会立即返回。
        socket.configureBlocking(false);
        // Optionally try first read now
        // 注册客户端 socket 到 Selector。
        // 这里先不设置感兴趣的事件，分离 register 和 interestOps 这两个操作，避免多线程下的竞争条件和同步问题。
        this.selectionKey = socket.register(selector, 0);
        // 把 Handler 自身放到 selectionKey 的附加属性中，用于在 IO 事件就绪时从 selectedKey 中获取 Handler，然后处理 IO 事件。
        this.selectionKey.attach(this);
        // 监听客户端连接上的 IO READ 事件
        this.selectionKey.interestOps(SelectionKey.OP_READ);

        // 由于 Selector 的注册信息发生变化，立即唤醒 Selector，让它能够处理最新订阅的 IO 事件
        selector.wakeup();
    }

    @Override
    public void run() {
        try {
            if (state == READING) {
                // 此时通道已经准备好读取数据
                read();
            } else if (state == SENDING) {
                // 此时通道已经准备好写入数据
                send();
            }
        } catch (IOException ex) {
            // 关闭连接
            try {
                selectionKey.channel().close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * 从通道读取字节
     */
    protected void read() throws IOException {
        // 清空 input buffer
        input.clear();
        // 读取内容到接收 input buffer
        int n = socket.read(input);
        // 判断用户是否输入完成
        if (inputIsComplete(n)) {
            // 用户输入完成，进行处理，将用户输入放入 output buffer
            process();
            // 修改 Handler 状态为响应
            state = SENDING;
            // 修改 channel select 的事件类型
            // Normally also do first write now
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * 当读取到 \r\n 时表示结束，切换到响应状态
     *
     * @param bytes 读取的字节数
     *              -1：到达了流的末尾，连接已经关闭
     *              0：当前没有可用数据，连接仍打开，通常在非阻塞模式下返回
     *              > 0：读取的字节数
     * @throws IOException
     */
    protected boolean inputIsComplete(int bytes) throws IOException {
        if (bytes > 0) {
            // 将 ByteBuffer 切换成读取模式
            input.flip();
            // 每次读取一个字符，添加到 inputStringBuilder，如果读到换行符则结束读取
            while (input.hasRemaining()) {
                byte ch = input.get();

                if (ch == 3) { // ctrl+c 关闭连接
                    state = CLOSED;
                    return true;
                } else if (ch == '\r') { // continue
                } else if (ch == '\n') {
                    // 读取到了 \r\n，读取结束
                    return true;
                } else {
                    inputStringBuilder.append((char) ch);
                }
            }
        } else if (bytes == -1) {
            // -1 客户端关闭了连接
            throw new EOFException();
        } else {
            // bytes == 0 继续读取
        }
        return false;
    }

    /**
     * 进行业务处理，将用户输入转换成大写
     *
     * @throws EOFException 用户输入 ctrl+c 主动关闭
     */
    protected void process() throws EOFException {
        // 构造用户输入内容字符串
        String requestContent = inputStringBuilder.toString();
        // 构造响应
        byte[] response = requestContent.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        output.put(response);
    }

    /**
     * 发送响应
     */
    protected void send() throws IOException {
        int written = -1;
        // 切换到读取模式，读取 output buffer，判断是否有数据要发送
        output.flip();
        // 如果有数据需要发送，则调用 socket.write 方法发送响应
        if (output.hasRemaining()) {
            written = socket.write(output);
        }

        // 检查连接是否处理完毕，是否断开连接
        if (outputIsComplete(written)) {
            selectionKey.channel().close();
        } else {
            // 否则继续读取
            state = READING;
            // 把提示发到界面
            socket.write(ByteBuffer.wrap("\r\nreactor> ".getBytes()));
            selectionKey.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 当用户输入了一个空行，表示连接可以关闭了
     */
    protected boolean outputIsComplete(int written) {
        if (written <= 0) {
            // 用户只敲了个回车， 断开连接
            return true;
        }

        // 清空旧数据，接着处理后续的请求
        output.clear();
        inputStringBuilder.delete(0, inputStringBuilder.length());
        return false;
    }
}
