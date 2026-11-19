package vn.edu.sgu.iot.mqtt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * {@link MqttTransport} on top of Eclipse Paho. Each {@code connect} creates a {@link Session};
 * a session that is no longer {@code current} (after disconnect, close or a newer connect) is
 * ignored by its own Paho callbacks, so stale events never reach the listener. Blocking Paho
 * teardown runs on a private worker thread, never on the caller.
 *
 * <p>Reconnects are driven here rather than by Paho's {@code automaticReconnect}: in Paho 1.2.5
 * that feature has no way to cancel its retry timer after {@code disconnect}/{@code close}, so a
 * disconnected client would keep a thread and keep retrying.
 */
public final class PahoTransport implements MqttTransport {
    private static final Logger LOG = Logger.getLogger(PahoTransport.class.getName());
    private static final int SUBACK_FAILURE = 0x80;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int KEEP_ALIVE_SECONDS = 15;
    private static final long DISCONNECT_WAIT_MILLIS = 2000;
    private static final long RECONNECT_INITIAL_DELAY_MILLIS = 1000;
    private static final long RECONNECT_MAX_DELAY_MILLIS = 10_000;

    private final Map<String, Integer> startupSubscriptions;
    private final ScheduledThreadPoolExecutor worker = newWorker();
    private final Object lock = new Object();
    private volatile Session current; // written under lock
    private boolean closed; // guarded by lock

    /** Subscribes to the three standard topics: sensor/data, sensor/led, sensor/status. */
    public PahoTransport() {
        this(standardSubscriptions());
    }

    /** Subscribes to exactly {@code startupSubscriptions} (topic filter to QoS 0 or 1). */
    public PahoTransport(Map<String, Integer> startupSubscriptions) {
        Objects.requireNonNull(startupSubscriptions, "startupSubscriptions");
        if (startupSubscriptions.isEmpty()) {
            throw new IllegalArgumentException("At least one subscription is required");
        }
        var copy = new LinkedHashMap<String, Integer>();
        for (var entry : startupSubscriptions.entrySet()) {
            String filter = Objects.requireNonNull(entry.getKey(), "topic filter");
            Integer qos = Objects.requireNonNull(entry.getValue(), "qos");
            if (filter.isEmpty()) {
                throw new IllegalArgumentException("Empty topic filter");
            }
            if (qos != 0 && qos != 1) {
                throw new IllegalArgumentException("Subscription QoS must be 0 or 1: " + filter + "=" + qos);
            }
            copy.put(filter, qos);
        }
        this.startupSubscriptions = Collections.unmodifiableMap(copy);
    }

    private static ScheduledThreadPoolExecutor newWorker() {
        var executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            var thread = new Thread(runnable, "paho-transport-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static Map<String, Integer> standardSubscriptions() {
        var map = new LinkedHashMap<String, Integer>();
        map.put("sensor/data", 0);
        map.put("sensor/led", 0);
        map.put("sensor/status", 1);
        return map;
    }

    @Override
    public CompletionStage<Void> connect(ConnectionSettings settings, Listener listener) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(listener, "listener");
        Session session = new Session(settings, listener);
        Session old;
        synchronized (lock) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Transport closed"));
            }
            old = current;
            current = session;
            if (old != null) {
                worker.execute(() -> old.teardown(false));
            }
            worker.execute(session::start);
        }
        return session.firstConnection;
    }

    @Override
    public CompletionStage<Void> disconnect() {
        Session old;
        synchronized (lock) {
            old = current;
            current = null;
            if (old == null || closed) {
                return CompletableFuture.completedFuture(null);
            }
            var done = new CompletableFuture<Void>();
            worker.execute(() -> {
                try {
                    old.teardown(true);
                } finally {
                    done.complete(null);
                }
            });
            return done;
        }
    }

    @Override
    public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
        Session session = current;
        MqttAsyncClient client = session == null ? null : session.client;
        if (client == null || !client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Broker disconnected"));
        }
        var result = new CompletableFuture<Void>();
        try {
            var message = new MqttMessage(payload.clone());
            message.setQos(qos);
            message.setRetained(retain);
            client.publish(topic, message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    result.complete(null);
                }

                @Override
                public void onFailure(IMqttToken token, Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (MqttException | RuntimeException ex) {
            result.completeExceptionally(ex);
        }
        return result;
    }

    @Override
    public boolean isConnected() {
        Session session = current;
        MqttAsyncClient client = session == null ? null : session.client;
        return client != null && client.isConnected();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            Session old = current;
            current = null;
            if (old != null) {
                worker.execute(() -> old.teardown(true));
            }
            worker.shutdown(); // queued teardown still runs
        }
    }

    /** One connect attempt and everything that follows from it until it is retired. */
    private final class Session implements MqttCallback {
        final ConnectionSettings settings;
        final Listener listener;
        final CompletableFuture<Void> firstConnection = new CompletableFuture<>();
        volatile MqttAsyncClient client;
        private MqttConnectOptions options; // worker thread only
        private final AtomicInteger reconnectAttempts = new AtomicInteger();

        Session(ConnectionSettings settings, Listener listener) {
            this.settings = settings;
            this.listener = listener;
        }

        private boolean isCurrent() {
            return current == this;
        }

        /** Worker thread. */
        void start() {
            if (!isCurrent()) {
                firstConnection.completeExceptionally(new IllegalStateException("Connection cancelled"));
                return;
            }
            emit(ConnectionState.CONNECTING);
            try {
                options = new MqttConnectOptions();
                options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                options.setCleanSession(true);
                options.setUserName(settings.username());
                options.setPassword(settings.password().toCharArray());
                options.setAutomaticReconnect(false);
                options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
                options.setConnectionTimeout(CONNECT_TIMEOUT_SECONDS);
                var created = new MqttAsyncClient(
                    settings.uri(), "java-ui-" + UUID.randomUUID(), new MemoryPersistence());
                created.setCallback(this);
                client = created;
                created.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken token) {
                        subscribeStartup();
                    }

                    @Override
                    public void onFailure(IMqttToken token, Throwable error) {
                        fail(error);
                    }
                });
            } catch (MqttException | RuntimeException ex) {
                fail(ex);
            }
        }

        /** Schedules the next reconnect attempt with a capped exponential backoff. */
        private void scheduleReconnect() {
            int attempt = reconnectAttempts.getAndIncrement();
            long delay = Math.min(RECONNECT_INITIAL_DELAY_MILLIS << Math.min(attempt, 10),
                RECONNECT_MAX_DELAY_MILLIS);
            try {
                worker.schedule(this::reconnect, delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                // transport closed meanwhile
            }
        }

        /** Worker thread: one reconnect attempt on the same Paho client (clean session). */
        private void reconnect() {
            MqttAsyncClient c = client;
            if (c == null || !isCurrent()) {
                return;
            }
            try {
                c.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken token) {
                        subscribeStartup(); // the broker forgot our subscriptions
                    }

                    @Override
                    public void onFailure(IMqttToken token, Throwable error) {
                        retryOrFail(error);
                    }
                });
            } catch (MqttException | RuntimeException ex) {
                retryOrFail(ex);
            }
        }

        /** Wrong credentials never recover by retrying; anything else does. */
        private void retryOrFail(Throwable error) {
            if (!isCurrent()) {
                return;
            }
            if (isAuthFailure(error)) {
                fail(error);
            } else {
                scheduleReconnect();
            }
        }

        private boolean isAuthFailure(Throwable error) {
            return error instanceof MqttException mqtt
                && (mqtt.getReasonCode() == MqttException.REASON_CODE_FAILED_AUTHENTICATION
                    || mqtt.getReasonCode() == MqttException.REASON_CODE_NOT_AUTHORIZED);
        }

        private void subscribeStartup() {
            MqttAsyncClient c = client;
            if (c == null || !isCurrent()) {
                return;
            }
            var entries = new ArrayList<>(startupSubscriptions.entrySet());
            String[] topics = entries.stream().map(Map.Entry::getKey).toArray(String[]::new);
            int[] qos = entries.stream().mapToInt(Map.Entry::getValue).toArray();
            try {
                c.subscribe(topics, qos, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken token) {
                        if (!isCurrent()) {
                            return;
                        }
                        // A grant lower than requested is fine (the MVP broker always grants QoS 0).
                        int[] granted = token.getGrantedQos();
                        if (granted != null) {
                            for (int g : granted) {
                                if (g == SUBACK_FAILURE) {
                                    fail(new MqttException(MqttException.REASON_CODE_SUBSCRIBE_FAILED));
                                    return;
                                }
                            }
                        }
                        reconnectAttempts.set(0);
                        emit(ConnectionState.CONNECTED);
                        firstConnection.complete(null);
                    }

                    @Override
                    public void onFailure(IMqttToken token, Throwable error) {
                        fail(error);
                    }
                });
            } catch (MqttException | RuntimeException ex) {
                fail(ex);
            }
        }

        /** Reports the error once, retires this session (no retry loop) and fails the connect future. */
        private void fail(Throwable error) {
            synchronized (lock) {
                if (current != this) {
                    return;
                }
                current = null;
            }
            emit(ConnectionState.ERROR);
            safely(() -> listener.onError(describe(error)));
            firstConnection.completeExceptionally(error);
            if (!worker.isShutdown()) {
                worker.execute(() -> teardown(false));
            }
        }

        /** Never includes the password: only host, port and Paho's reason code are used. */
        private String describe(Throwable error) {
            String where = settings.host() + ":" + settings.port();
            if (error instanceof MqttException mqtt) {
                return switch (mqtt.getReasonCode()) {
                    case MqttException.REASON_CODE_FAILED_AUTHENTICATION,
                         MqttException.REASON_CODE_NOT_AUTHORIZED ->
                        "Authentication failed: check the username and password";
                    case MqttException.REASON_CODE_BROKER_UNAVAILABLE -> "Broker unavailable at " + where;
                    case MqttException.REASON_CODE_SERVER_CONNECT_ERROR -> "Cannot reach broker at " + where;
                    case MqttException.REASON_CODE_CLIENT_TIMEOUT,
                         MqttException.REASON_CODE_CONNECT_IN_PROGRESS ->
                        "Timed out connecting to " + where;
                    case MqttException.REASON_CODE_SUBSCRIBE_FAILED -> "Broker refused the subscription";
                    default -> "MQTT error (reason code " + mqtt.getReasonCode() + ")";
                };
            }
            return error.getClass().getSimpleName();
        }

        /** Worker thread. Stops Paho (including any reconnect cycle) and optionally reports DISCONNECTED. */
        void teardown(boolean reportDisconnected) {
            MqttAsyncClient c = client;
            client = null;
            firstConnection.completeExceptionally(new IllegalStateException("Disconnected"));
            if (c != null) {
                try {
                    if (c.isConnected()) {
                        c.disconnect(0).waitForCompletion(DISCONNECT_WAIT_MILLIS);
                    }
                } catch (MqttException | RuntimeException ex) {
                    LOG.log(Level.FINE, "Graceful disconnect failed", ex);
                }
                try {
                    c.close(true);
                } catch (MqttException | RuntimeException ex) {
                    LOG.log(Level.FINE, "Close failed", ex);
                }
            }
            if (reportDisconnected) {
                emit(ConnectionState.DISCONNECTED);
            }
        }

        private void emit(ConnectionState state) {
            safely(() -> listener.onConnection(state));
        }

        private void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Listener threw", ex);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            if (isCurrent()) {
                emit(ConnectionState.RECONNECTING);
                if (!worker.isShutdown()) {
                    worker.execute(this::scheduleReconnect);
                }
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            if (isCurrent()) {
                safely(() -> listener.onMessage(topic, message.getPayload().clone()));
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // publish() completes from its own action listener
        }
    }
}
