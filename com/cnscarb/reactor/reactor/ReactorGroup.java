package com.cnscarb.reactor.reactor;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class ReactorGroup {
    /**
     * Reactor 数组，保存当前 ReactorGroup 下的所有 Reactor
     */
    final Reactor[] children;

    /**
     * 计数器，用来选择下一个 Reactor
     */
    int next = 0;

    public ReactorGroup(int nThreads) {
        children = new Reactor[nThreads];

        for (int i = 0; i < nThreads; i++) {
            try {
                children[i] = new Reactor();
            } catch (IOException e) {
            }
        }
    }

    public Reactor next() {
        final Reactor reactor = children[next];
        if (++next == children.length) {
            next = 0;
        }
        return reactor;
    }

    /**
     * 注册 ServerSocketChannel 到 ReactorGroup 中的下一个选中的 Reactor
     */
    public SelectionKey register(ServerSocketChannel serverSocket) throws ClosedChannelException {
        return next().register(serverSocket);
    }
}
