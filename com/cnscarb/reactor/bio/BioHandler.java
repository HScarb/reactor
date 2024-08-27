package com.cnscarb.reactor.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Locale;

/**
 * 处理单个客户端连接的具体逻辑
 */
public class BioHandler implements Runnable {

    public Socket socket;

    public BioHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("New client connected");
        try (
            final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            final PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        ) {
            writer.print("bio> ");
            writer.flush();
            String input;
            // 读取客户端输入的一行内容
            while ((input = reader.readLine()) != null) {
                // 处理客户端输入的内容
                final String output = process(input);
                // 将处理后的内容写回给客户端
                writer.println(output);
                writer.print("bio> ");
                writer.flush();
            }
        } catch (IOException e) {
            System.out.println("Error handling io: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Failed to close socket: " + e.getMessage());
            }
        }
    }

    /**
     * 将客户端输入的内容转换为大写
     */
    private String process(String requestContent) {
        return requestContent.toUpperCase(Locale.ROOT);
    }
}
