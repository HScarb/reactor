package com.cnscarb.reactor.reactor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Reactor implements Runnable {
    /**
     * 选择器，NIO 组件，通知 Channel 就绪的事件
     */
    final Selector selector;

    /**
     * Handler 的类型
     */
    final Class<?> handlerClass;

    /**
     * TCP 服务端 Socket，监听某个端口进来的客户端连接和请求
     */
    ServerSocketChannel serverSocket;

    /**
     * Reactor 的执行线程
     */
    public final ExecutorService executor;

    /**
     * 直接创建 Reactor 使用
     */
    public Reactor(int port, Class<?> handlerClass) throws IOException {
        this.handlerClass = handlerClass;
        executor = Executors.newSingleThreadExecutor();
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        // 绑定服务端端口
        serverSocket.socket().bind(new InetSocketAddress(port));
        // 设置服务端 socket 为非阻塞模式
        serverSocket.configureBlocking(false);
        // 注册并关注一个 IO 事件，这里是 ACCEPT（接收客户端连接）
        final SelectionKey selectionKey = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        // 将 Acceptor 作为附件关联到 SelectionKey 上，用于在客户端连接事件发生时取出，让 Acceptor 去分发连接给 Handler
        selectionKey.attach(new Acceptor());
    }

    /**
     * {@link ReactorGroup} 创建 Reactor 使用
     */
    public Reactor() throws IOException {
        executor = Executors.newSingleThreadExecutor();
        selector = Selector.open();
        this.handlerClass = null;
    }

    @Override
    public void run() { // normally in a new Thread
        try {
            // 死循环，直到线程停止
            while (!Thread.interrupted()) {
                // 阻塞，直到至少有一个通道的 IO 事件就绪
                selector.select();
                // 拿到就绪通道的选择键 SelectionKey 集合
                final Set<SelectionKey> selectedKeys = selector.selectedKeys();
                // 遍历就绪通道的 SelectionKey
                final Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    // 分发
                    dispatch(iterator.next());
                }
                // 清空就绪通道的 SelectionKey 集合
                selectedKeys.clear();
            }
        } catch (IOException e) {
        }
    }

    /**
     * 分发事件，将就绪通道的注册键关联的处理器取出并执行
     * <p>
     * 在 MainReactor 中，就绪的是客户端连接事件，处理器是 Acceptor
     * <p>
     * 在 SubReactor 中，就绪的是客户端 IO 事件，处理器是 Handler
     */
    private void dispatch(SelectionKey selectionKey) {
        // 获取 Selection 关联的处理器
        final Runnable runnable = (Runnable) selectionKey.attachment();
        if (runnable != null) {
            // 执行处理
            runnable.run();
        }
    }

    /**
     * 启动 Reactor 线程，执行 run 方法
     */
    public void startThread() {
        executor.execute(this);
    }

    public SelectionKey register(ServerSocketChannel serverSocket) throws ClosedChannelException {
        SelectionKey selectionKey = serverSocket.register(selector, 0);
        startThread();
        return selectionKey;
    }

    /**
     * 处理客户端连接事件
     */
    class Acceptor implements Runnable {
        @Override
        public void run() {
            try {
                // 接收客户端连接，返回客户端 SocketChannel。非阻塞模式下，没有客户端连接则直接返回 null
                final SocketChannel socket = serverSocket.accept();
                if (socket != null) {
                    // 将提示发送给客户端
                    socket.write(ByteBuffer.wrap("reactor> ".getBytes()));
                    // 根据 Handler 类型，实例化 Handler
                    final Constructor<?> constructor = handlerClass.getConstructor(Selector.class, SocketChannel.class);
                    // 在 Handler 线程中处理客户端 IO 事件
                    constructor.newInstance(selector, socket);
                }
            } catch (Exception e) {
            }
        }
    }
}
