package com.cnscarb.reactor.reactor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class MultiReactorBootstrap {
    /**
     * 主 Reactor 组
     */
    private ReactorGroup mainReactorGroup;

    /**
     * 从 Reactor 组
     */
    private ReactorGroup subReactorGroup;

    private final ServerSocketChannel serverSocket;

    private final Class<?> handlerClass;

    public MultiReactorBootstrap(int port, ReactorGroup mainReactorGroup, ReactorGroup subReactorGroup,
        Class<?> handlerClass) throws IOException {
        this.mainReactorGroup = mainReactorGroup;
        this.subReactorGroup = subReactorGroup;
        this.handlerClass = handlerClass;

        // 将服务端 ServerSocketChannel 绑定到端口上
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        // 让 Main Reactor 监听 ServerSocketChannel 上的 ACCEPT 事件
        SelectionKey selectionKey = this.mainReactorGroup.register(serverSocket);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        selectionKey.attach(new Acceptor());
    }

    private class Acceptor implements Runnable {

        @Override
        public synchronized void run() {
            try {
                SocketChannel socket = serverSocket.accept();
                if (socket != null) {
                    socket.write(ByteBuffer.wrap("reactor> ".getBytes()));
                    // 从 Sub Reactor 组中轮询选择一个 Reactor，用于处理新的客户端连接
                    final Reactor subReactor = subReactorGroup.next();

                    // 实例化 Handler
                    final Constructor<?> constructor = handlerClass.getConstructor(Selector.class, SocketChannel.class);
                    // 将客户端 SocketChannel 注册到 Sub Reactor 的 Selector 上
                    constructor.newInstance(subReactor.selector, socket);
                    // 启动 Sub Reactor 线程，开始监听客户端 SocketChannel 上的 IO 事件
                    subReactor.startThread();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
