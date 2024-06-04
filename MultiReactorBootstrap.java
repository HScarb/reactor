import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MultiReactorBootstrap {

    static final int WORKER_POOL_SIZE = 4;

    static Executor mainPool = Executors.newFixedThreadPool(1);

    static Executor workerPool = Executors.newFixedThreadPool(WORKER_POOL_SIZE);

    static Reactor mainReactor;

    static Reactor[] subReactors = new Reactor[WORKER_POOL_SIZE];

    static int next = 0;

    public static void main(String[] args) throws IOException {
        try {
            Acceptor multiReactorAcceptor = new Acceptor();
            mainReactor = new Reactor(10393, multiReactorAcceptor);
            Thread mainThread = new Thread(mainReactor);
            mainThread.setName("main-reactor");
            mainPool.execute(mainThread);

            for (int i = 0; i < subReactors.length; i++) {
                subReactors[i] = new Reactor();
                Thread subThread = new Thread(subReactors[i]);
                subThread.setName("sub-reactor-" + i);
                workerPool.execute(subThread);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Acceptor implements Runnable {

        @Override
        public synchronized void run() {
            try {
                SocketChannel connection = mainReactor.serverSocket.accept();
                if (connection != null) {
                    connection.write(ByteBuffer.wrap("reactor> ".getBytes()));
                    System.out.println("Accept and handler - " + connection.socket().getLocalSocketAddress());
                    Reactor subReactor = subReactors[next];
                    subReactor.register(connection);
                }
                if (++next == subReactors.length) {
                    next = 0;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
