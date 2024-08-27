package com.cnscarb.reactor.bio;

import java.io.IOException;
import java.net.ServerSocket;

public class BioServer implements Runnable {
    public int port;

    public BioServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);
            while (!Thread.interrupted()) {
                try {
                    // 当有新的客户端连接时，accept() 方法会返回一个Socket对象，表示与客户端的连接
                    // 创建一个新的线程来处理该连接
                    new Thread(new BioHandler(serverSocket.accept())).start();
                } catch (IOException e) {
                    System.out.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }
}
