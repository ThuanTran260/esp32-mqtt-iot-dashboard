package vn.edu.sgu.mqtt.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.edu.sgu.mqtt.routing.RetainedStore;
import vn.edu.sgu.mqtt.routing.SubscriptionRouter;
import vn.edu.sgu.mqtt.server.ClientSession.CloseReason;

/** TCP listener that hands each accepted socket to a {@link ClientSession} virtual thread. */
public final class BrokerServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(BrokerServer.class.getName());
    private static final int MAX_CONSECUTIVE_ACCEPT_FAILURES = 3;
    private static final long TERMINATION_WAIT_SECONDS = 3;

    private final BrokerConfig config;
    private final Duration connectTimeout;
    private final ClientRegistry registry = new ClientRegistry();
    private final SubscriptionRouter router = new SubscriptionRouter(new RetainedStore());
    /** Every live session, including ones that have not authenticated yet. */
    private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ServerSocket serverSocket;
    private volatile int localPort = -1;
    private int consecutiveAcceptFailures; // acceptor thread only

    public BrokerServer(BrokerConfig config) {
        this(config, ClientSession.DEFAULT_CONNECT_TIMEOUT);
    }

    /** Lets tests shorten the CONNECT deadline. */
    BrokerServer(BrokerConfig config, Duration connectTimeout) {
        this.config = config;
        this.connectTimeout = connectTimeout;
    }

    /** Binds the listener and starts accepting; returns only after the bind succeeded. */
    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Already started");
        var listener = new ServerSocket();
        try {
            listener.bind(new InetSocketAddress(config.bindAddress(), config.port()));
        } catch (IOException | RuntimeException ex) {
            listener.close();
            throw ex;
        }
        serverSocket = listener;
        localPort = listener.getLocalPort();
        workers.execute(() -> acceptLoop(listener));
    }

    /** The router shared by every session (exposed for white-box tests). */
    public SubscriptionRouter router() {
        return router;
    }

    /** The bound port (the real one when configured with port 0). */
    public int port() {
        return localPort;
    }

    private void acceptLoop(ServerSocket listener) {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                consecutiveAcceptFailures = 0;
                acceptConnection(socket);
            } catch (IOException ex) {
                if (!listener.isClosed()) recordAcceptFailure(ex, listener);
            }
        }
    }

    private void acceptConnection(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            var session = new ClientSession(socket, config, registry, connectTimeout, sessions::remove, router);
            sessions.add(session);
            if (closed.get()) {
                session.close(CloseReason.SHUTDOWN); // raced with close(): sweep already ran
                return;
            }
            workers.execute(session);
        } catch (IOException | RejectedExecutionException ex) {
            LOG.log(Level.FINE, "Dropping connection", ex);
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing more to do
            }
        }
    }

    private void recordAcceptFailure(IOException ex, ServerSocket listener) {
        consecutiveAcceptFailures++;
        LOG.log(Level.WARNING, "Accept failed ({0}): {1}",
            new Object[] {consecutiveAcceptFailures, ex.toString()});
        if (consecutiveAcceptFailures >= MAX_CONSECUTIVE_ACCEPT_FAILURES) {
            LOG.warning("Closing listener after repeated accept failures");
            try {
                listener.close();
            } catch (IOException ignored) {
                // already failing
            }
        }
    }

    /** Stops accepting, closes every session and waits up to 3 s for workers to finish. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ServerSocket listener = serverSocket;
        if (listener != null) {
            try {
                listener.close();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Listener close failed", ex);
            }
        }
        for (ClientSession session : sessions) session.close(CloseReason.SHUTDOWN);
        workers.shutdown();
        try {
            if (!workers.awaitTermination(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("Workers did not terminate within 3 s");
                workers.shutdownNow();
            }
        } catch (InterruptedException ex) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
