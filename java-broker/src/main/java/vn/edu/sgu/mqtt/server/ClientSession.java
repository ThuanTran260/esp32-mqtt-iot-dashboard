package vn.edu.sgu.mqtt.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.edu.sgu.mqtt.protocol.FrameCodec;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Connect;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Will;
import vn.edu.sgu.mqtt.protocol.Packet;
import vn.edu.sgu.mqtt.protocol.ProtocolException;
import vn.edu.sgu.mqtt.routing.SubscriptionRouter;

/**
 * One client connection: NEW (only CONNECT accepted) → CONNECTED → CLOSED.
 *
 * <p>Timing uses {@link Socket#setSoTimeout}: {@code connectTimeout} until CONNECT arrives,
 * then 1.5 × keep-alive (no timeout when keep-alive is 0). All outbound writes go through
 * {@link #send(Packet)}, which serializes them per session.
 */
public final class ClientSession implements Runnable, AutoCloseable {
    public enum CloseReason { GRACEFUL, NETWORK, PROTOCOL, KEEPALIVE, REPLACED, SHUTDOWN, OVERLOAD }

    private enum State { NEW, CONNECTED, CLOSED }

    /** Default time a new connection has to deliver its CONNECT packet. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger LOG = Logger.getLogger(ClientSession.class.getName());
    private static final int MAX_INBOUND_BODY = 65536;

    private final Socket socket;
    private final BrokerConfig config;
    private final ClientRegistry registry;
    private final SubscriptionRouter router;
    private final Duration connectTimeout;
    private final Consumer<ClientSession> onClosed;
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile State state = State.NEW;
    private volatile CloseReason closeReason;
    private volatile String clientId;
    private volatile Will will;         // captured from CONNECT, published on abnormal close
    private volatile boolean accepted;  // true once CONNACK 0 was sent
    private OutputStream out; // guarded by this

    public ClientSession(Socket socket, BrokerConfig config, ClientRegistry registry,
                         SubscriptionRouter router) {
        this(socket, config, registry, DEFAULT_CONNECT_TIMEOUT, session -> { }, router);
    }

    /** Lets tests shorten the CONNECT deadline; {@code onClosed} runs once after cleanup. */
    ClientSession(Socket socket, BrokerConfig config, ClientRegistry registry,
                  Duration connectTimeout, Consumer<ClientSession> onClosed,
                  SubscriptionRouter router) {
        this.socket = socket;
        this.config = config;
        this.registry = registry;
        this.router = router;
        this.connectTimeout = connectTimeout;
        this.onClosed = onClosed;
    }

    /** The ClientId once CONNECT was accepted, otherwise null. */
    public String clientId() {
        return clientId;
    }

    /** Why the session closed, or null while it is open. */
    public CloseReason closeReason() {
        return closeReason;
    }

    public boolean isClosed() {
        return closed.get();
    }

    // ------------------------------------------------------------------ outbound

    /** Writes and flushes one packet; concurrent callers are serialized. */
    public synchronized void send(Packet packet) throws IOException {
        if (closed.get()) throw new IOException("Session closed");
        if (out == null) out = new BufferedOutputStream(socket.getOutputStream());
        FrameCodec.write(out, packet);
        out.flush();
    }

    /** Sends the packet; returns false (and closes the session) if the write fails. */
    public boolean enqueue(Packet packet) {
        try {
            send(packet);
            return true;
        } catch (IOException ex) {
            close(CloseReason.NETWORK);
            return false;
        }
    }

    /** Encodes and sends an outbound PUBLISH; a failed write closes the session. */
    public void sendPublish(Publish publish) {
        enqueue(MqttCodec.publish(publish));
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void run() {
        try {
            socket.setSoTimeout(timeoutMillis(connectTimeout.toMillis()));
            InputStream in = new BufferedInputStream(socket.getInputStream());
            while (!closed.get()) {
                Packet packet = FrameCodec.read(in, MAX_INBOUND_BODY);
                if (packet == null) {
                    close(CloseReason.NETWORK);
                } else {
                    handle(packet);
                }
            }
        } catch (SocketTimeoutException ex) {
            close(state == State.NEW ? CloseReason.PROTOCOL : CloseReason.KEEPALIVE);
        } catch (ProtocolException ex) {
            if (ex.connackCode() >= 0 && state == State.NEW) {
                sendQuietly(MqttCodec.connack(ex.connackCode()));
            }
            LOG.log(Level.FINE, "Protocol violation: {0}", ex.getMessage());
            close(CloseReason.PROTOCOL);
        } catch (IOException ex) {
            close(CloseReason.NETWORK);
        } finally {
            close(CloseReason.NETWORK); // no-op unless an unexpected exception skipped the above
        }
    }

    private void handle(Packet packet) throws IOException {
        if (state == State.NEW) {
            if (packet.type() != MqttCodec.CONNECT) {
                throw new ProtocolException("First packet must be CONNECT");
            }
            onConnect(packet);
            return;
        }
        switch (packet.type()) {
            case MqttCodec.PINGREQ -> {
                MqttCodec.checkPingreq(packet);
                send(MqttCodec.pingresp());
            }
            case MqttCodec.DISCONNECT -> {
                MqttCodec.checkDisconnect(packet);
                close(CloseReason.GRACEFUL);
            }
            case MqttCodec.SUBSCRIBE ->
                router.subscribe(this, MqttCodec.decodeSubscribe(packet));
            case MqttCodec.UNSUBSCRIBE ->
                router.unsubscribe(this, MqttCodec.decodeUnsubscribe(packet));
            case MqttCodec.PUBLISH -> onPublish(packet);
            // CONNECT again and every other type violate the protocol.
            default -> throw new ProtocolException("Unexpected packet type " + packet.type());
        }
    }

    private void onPublish(Packet packet) throws IOException {
        Publish publish = MqttCodec.decodePublish(packet);
        if (publish.qos() == 2) throw new ProtocolException("QoS 2 is not supported");
        if (closed.get()) return; // no publications after close
        router.route(publish);
        if (publish.qos() == 1) send(MqttCodec.puback(publish.packetId()));
    }

    private void onConnect(Packet packet) throws IOException {
        Connect connect = MqttCodec.decodeConnect(packet);
        if (!connect.cleanSession() || connect.clientId().isEmpty()) {
            rejectAndClose(MqttCodec.CONNACK_IDENTIFIER_REJECTED);
            return;
        }
        if (connect.will() != null && connect.will().qos() == 2) {
            rejectAndClose(3);
            return;
        }
        if (!credentialsMatch(connect)) {
            rejectAndClose(4);
            return;
        }
        clientId = connect.clientId();
        will = connect.will();
        state = State.CONNECTED;
        registry.register(clientId, this);
        int keepAlive = connect.keepAliveSeconds();
        socket.setSoTimeout(keepAlive == 0 ? 0 : keepAlive * 1500);
        send(MqttCodec.connack(0));
        accepted = true;
    }

    private void rejectAndClose(int connackCode) {
        sendQuietly(MqttCodec.connack(connackCode));
        close(CloseReason.PROTOCOL);
    }

    private void sendQuietly(Packet packet) {
        try {
            send(packet);
        } catch (IOException ex) {
            // best effort before closing
        }
    }

    private boolean credentialsMatch(Connect connect) {
        if (connect.username() == null || connect.password() == null) return false;
        boolean user = MessageDigest.isEqual(
            connect.username().getBytes(StandardCharsets.UTF_8),
            config.username().getBytes(StandardCharsets.UTF_8));
        boolean password = MessageDigest.isEqual(
            connect.password(), config.password().getBytes(StandardCharsets.UTF_8));
        return user & password;
    }

    private static int timeoutMillis(long millis) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, millis));
    }

    /** Idempotent: the first caller's reason wins. */
    public void close(CloseReason reason) {
        if (!closed.compareAndSet(false, true)) return;
        closeReason = reason;
        state = State.CLOSED;
        try {
            socket.close();
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Socket close failed", ex);
        }
        String id = clientId;
        if (id != null) registry.remove(id, this);
        router.remove(this);
        publishWill(reason);
        onClosed.accept(this);
    }

    /** Runs at most once (guarded by the close CAS): any close except GRACEFUL sends the Will. */
    private void publishWill(CloseReason reason) {
        Will captured = will;
        if (!accepted || captured == null || reason == CloseReason.GRACEFUL) return;
        try {
            router.route(new Publish(captured.topic(), captured.payload().clone(),
                captured.qos(), captured.retain(), false, 0));
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Publishing Will of {0} failed: {1}",
                new Object[] {clientId, ex.toString()});
        }
    }

    @Override
    public void close() {
        close(CloseReason.SHUTDOWN);
    }

    @Override
    public String toString() {
        return "ClientSession[clientId=" + clientId + ", state=" + state + "]";
    }
}
