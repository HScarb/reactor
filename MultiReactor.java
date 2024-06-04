import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 为了匹配 CPU 和 IO 的速率，可设计多个 Reactor（即 Selector 池）：<p>
 * 主 Reactor 负责监听连接，然后将连接注册到从 Reactor，将 I/O 转移了<br>
 * 从 Reactor 负责通道 I/O 的读写，处理器可选择单线程或线程池
 * <p>
 * <b>注意这里的 Reactor 是 MultiReactor 的内部类</b>
 *
 * @author wskwbog
 * @see MultiReactor.Reactor
 */
public class MultiReactor {
    public static void main(String[] args) throws IOException {
        MultiReactor mr = new MultiReactor(10393);
        mr.start();
    }

    static final int WORKER_POOL_SIZE = 4;

    // Reactor（Selector） 线程池，其中一个线程被 mainReactor 使用，剩余线程都被 subReactor 使用
    static Executor mainPool = Executors.newFixedThreadPool(1);

    static Executor workerPool = Executors.newFixedThreadPool(WORKER_POOL_SIZE);

    /**
     * 主 Reactor，接收连接，把 SocketChannel 注册到从 Reactor 上
     */
    private Reactor mainReactor;

    /**
     * 从 Reactors，用于处理 I/O，可使用 BasicHandler 和 MultiThreadHandler 两种处理方式
     */
    private Reactor[] subReactors = new Reactor[WORKER_POOL_SIZE];

    int next = 0;

    private int port;

    public MultiReactor(int port) {
        try {
            this.port = port;
            mainReactor = new Reactor();

            for (int i = 0; i < subReactors.length; i++) {
                subReactors[i] = new Reactor();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动主从 Reactor，初始化并注册 Acceptor 到主 Reactor
     */
    public void start() throws IOException {
        Thread mrThread = new Thread(mainReactor);
        mrThread.setName("mainReactor");
        new Acceptor(mainReactor.getSelector(), port); // 将 ServerSocketChannel 注册到 mainReactor

        workerPool.execute(mrThread);

        for (int i = 0; i < subReactors.length; i++) {
            Thread srThread = new Thread(subReactors[i]);
            srThread.setName("subReactor-" + i);
            workerPool.execute(srThread);
        }
    }

    class Acceptor implements Runnable {
        final Selector sel;

        final ServerSocketChannel serverSocket;

        /**
         * 初始化并配置 ServerSocketChannel，注册到 mainReactor 的 Selector 上
         *
         * @param sel  mainReactor 的 Selector
         * @param port 监听的端口
         * @throws IOException
         */
        public Acceptor(Selector sel, int port) throws IOException {
            this.sel = sel;
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(port)); // 绑定端口
            // 设置成非阻塞模式
            serverSocket.configureBlocking(false);
            // 注册到 main reactor 的 Selector 并关注处理 socket 连接事件
            SelectionKey sk = serverSocket.register(sel, SelectionKey.OP_ACCEPT);
            sk.attach(this);
            System.out.println("mainReactor-" + "Acceptor: Listening on port: " + port);
        }

        @Override
        public synchronized void run() {
            try {
                // 接收连接，非阻塞模式下，没有连接直接返回 null
                SocketChannel connection = serverSocket.accept();
                if (connection != null) {
                    // 把提示发到界面
                    connection.write(ByteBuffer.wrap(
                        "Implementation of Reactor Design Pattern by tonwu.net\r\nreactor> ".getBytes()));

                    System.out.println(
                        "mainReactor-" + "Acceptor: " + connection.socket().getLocalSocketAddress() + " 注册到 subReactor-"
                            + next);
                    // 将接收的连接注册到从 Reactor 上

                    // 发现无法直接注册，一直获取不到锁，这是由于 从 Reactor 目前正阻塞在 select() 方法上，此方法已经
                    // 锁定了 publicKeys（已注册的key)，直接注册会造成死锁

                    // 如何解决呢，直接调用 wakeup，有可能还没有注册成功又阻塞了。这是一个多线程同步的问题，可以借助队列进行处理
                    Reactor subReactor = subReactors[next];
                    subReactor.register(new BasicHandler(connection));
                    // new MultithreadHandler(subSel, connection);
                    if (++next == subReactors.length) {
                        next = 0;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static class Reactor implements Runnable {
        private ConcurrentLinkedQueue<BasicHandler> events = new ConcurrentLinkedQueue<>();

        final Selector selector;

        public Reactor() throws IOException {
            selector = Selector.open();
        }

        public Selector getSelector() {
            return selector;
        }

        @Override
        public void run() { // normally in a new Thread
            try {
                while (!Thread.interrupted()) { // 死循环
                    BasicHandler handler = null;
                    // 处理 sub reactor 的注册事件
                    while ((handler = events.poll()) != null) {
                        handler.socket.configureBlocking(false); // 设置非阻塞
                        // Optionally try first read now
                        // 将连接注册到 Handler 的 Selector，关注 READ 事件
                        handler.selectionKey = handler.socket.register(selector, SelectionKey.OP_READ);
                        handler.selectionKey.attach(handler); // 将 Read 事件的处理类（Handler）附加到 SelectionKey 上
                    }

                    // reactor 主循环
                    selector.select(); // 阻塞，直到有通道事件就绪
                    Set<SelectionKey> selected = selector.selectedKeys(); // 拿到就绪通道 SelectionKey 的集合
                    Iterator<SelectionKey> it = selected.iterator();
                    while (it.hasNext()) {
                        SelectionKey skTmp = it.next();
                        dispatch(skTmp); // 根据 key 的事件类型进行分发
                    }
                    selected.clear(); // 清空就绪通道的 key
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * 事件分发
         */
        void dispatch(SelectionKey k) {
            // 拿到通道注册时附加的对象，main reactor 附加的是 Acceptor，sub reactor 附加的是 Handler
            Runnable r = (Runnable) (k.attachment());
            // 执行附加对象的 run 方法，main reactor 的 Acceptor 将连接事件分发给 sub reactor，sub reactor 会调用 Handler 处理
            if (r != null) {
                r.run();
            }
        }

        /**
         * 用于 sub reactor 收到新的连接事件时注册 Handler
         */
        void register(BasicHandler basicHandler) {
            events.offer(basicHandler);
            selector.wakeup();
        }
    }
}
