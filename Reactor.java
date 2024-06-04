import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Reactor 接收连接，直接负责 I/O 的读写，拿到字节后有单线程和多线程两种处理器：
 * <p>
 * 单线程处理器：业务处理也由 Reactor 线程来做
 * <br>
 * 多线程处理器：业务处理由线程池线程来做
 *
 * @author tongwu.net
 * @see BasicHandler
 * @see MultiThreadHandler
 */
public class Reactor implements Runnable {
    public static void main(String[] args) {
        try {
            Thread th = new Thread(new Reactor(10393));
            th.setName("Reactor");
            th.start();
            th.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 选择器，nio 组件，通知 Channel 就绪的事件
     */
    final Selector selector;

    /**
     * TCP 对应的服务端通道，用于监听某个端口进来的请求
     */
    final ServerSocketChannel serverSocket;

    public Reactor(int port) throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port)); // 绑定端口
        serverSocket.configureBlocking(false); // 设置成非阻塞模式
        // 注册并关注一个 IO 事件，这里是接收连接
        SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        // 将 Acceptor 作为附件关联到 SelectionKey 上，用于在事件发生时取出，让 Acceptor 去分发连接给 Handler
        sk.attach(new Acceptor());

        System.out.println("Listening on port " + port);
    }

    public Reactor(int port, MultiReactorBootstrap.Acceptor acceptor) throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port)); // 绑定端口
        serverSocket.configureBlocking(false); // 设置成非阻塞模式
        // 注册并关注一个 IO 事件，这里是接收连接
        SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        // 将 Acceptor 作为附件关联到 SelectionKey 上，用于在事件发生时取出，让 Acceptor 去分发连接给 Handler
        sk.attach(acceptor);

        System.out.println("Listening on port " + port);
    }

    public Reactor() throws IOException {
        selector = Selector.open();
        serverSocket = null;
    }

    @Override
    public void run() { // normally in a new Thread
        try {
            while (!Thread.interrupted()) { // 死循环
                selector.select(); // 阻塞，直到有通道事件就绪
                Set<SelectionKey> selected = selector.selectedKeys(); // 拿到就绪通道 SelectionKey 的集合
                Iterator<SelectionKey> it = selected.iterator();
                while (it.hasNext()) {
                    SelectionKey skTmp = it.next();
                    dispatch(skTmp); // 分发
                }
                selected.clear(); // 清空就绪通道的 key
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void dispatch(SelectionKey k) {
        Runnable r = (Runnable) (k.attachment()); // 获取key关联的处理器
        if (r != null) {
            r.run(); // 执行处理
        }
    }

    public void register(SocketChannel connection) throws IOException {
        new BasicHandler(selector, connection);
        // new MultiThreadHandler(selector, connection);
    }

    /**
     * 处理连接建立事件
     */
    class Acceptor implements Runnable {
        @Override
        public void run() {
            try {
                SocketChannel connection = serverSocket.accept(); // 接收连接，非阻塞模式下，没有连接直接返回 null
                if (connection != null) {
                    // 把提示发到界面
                    connection.write(ByteBuffer.wrap(
                        "Implementation of Reactor Design Pattern by tonwu.net\r\nreactor> ".getBytes()));
                    System.out.println("Accept and handler - " + connection.socket().getLocalSocketAddress());
                    // new BasicHandler(selector, connection); // 单线程处理连接
                    new MultiThreadHandler(selector, connection); // 线程池处理连接
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
