package vn.edu.sgu.iot.ui;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;

/**
 * UI-independent dashboard logic (no Swing imports). Transport callbacks arrive on transport
 * threads; every one of them, and every {@code whenComplete} result, is handed to the {@code ui}
 * executor before it touches state, so all state is confined to that executor's thread. Tests pass
 * {@code Runnable::run}; the Swing view passes {@code SwingUtilities::invokeLater}.
 *
 * <p>Each {@link #connect} starts a new session generation. Callbacks capture the generation they
 * were created for and are dropped when it is no longer current.
 */
public final class DashboardController {
    public static final String TOPIC_DATA = "sensor/data";
    public static final String TOPIC_LED = "sensor/led";
    public static final String TOPIC_STATUS = "sensor/status";

    static final int MAX_TELEMETRY_BYTES = 1024;
    static final int MAX_STATUS_BYTES = 64;
    static final int MAX_LOG_LINES = 50;
    static final double MIN_TEMP = -40;
    static final double MAX_TEMP = 80;
    static final double MIN_HUM = 0;
    static final double MAX_HUM = 100;

    private static final String NUM = "-?\\d+(?:\\.\\d+)?";
    // Strict flat object with exactly the two keys, in either order. Anything else (duplicate keys,
    // nesting, "temp" inside a string value, extra fields) simply does not match.
    private static final Pattern TEMP_FIRST = Pattern.compile(
        "\\s*\\{\\s*\"temp\"\\s*:\\s*(" + NUM + ")\\s*,\\s*\"hum\"\\s*:\\s*(" + NUM + ")\\s*}\\s*");
    private static final Pattern HUM_FIRST = Pattern.compile(
        "\\s*\\{\\s*\"hum\"\\s*:\\s*(" + NUM + ")\\s*,\\s*\"temp\"\\s*:\\s*(" + NUM + ")\\s*}\\s*");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum DeviceState { UNKNOWN, ONLINE, OFFLINE }

    /** Immutable view of everything the UI shows. Temperature and humidity are null until received. */
    public record Snapshot(ConnectionState broker, DeviceState device, Double temperature,
                           Double humidity, LocalTime lastUpdate, String message, List<String> log) {
        public Snapshot {
            log = List.copyOf(log);
        }
    }

    private final MqttTransport transport;
    private final Executor ui;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ArrayDeque<String> log = new ArrayDeque<>();

    private volatile Consumer<Snapshot> onChange = s -> { };
    private volatile Snapshot snapshot;

    // The fields below are only touched from the ui executor.
    private ConnectionState broker = ConnectionState.DISCONNECTED;
    private DeviceState device = DeviceState.UNKNOWN;
    private Double temperature;
    private Double humidity;
    private LocalTime lastUpdate;
    private String message = "Not connected";
    private String password = "";
    private boolean errorReported;

    public DashboardController(MqttTransport transport, Executor ui) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.snapshot = buildSnapshot();
    }

    /** Called on the {@code ui} executor after every state change. */
    public void setOnChange(Consumer<Snapshot> onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /** Starts a new session; any callback of an earlier session is ignored from now on. */
    public void connect(ConnectionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (closed.get()) {
            return;
        }
        int gen = generation.incrementAndGet();
        password = settings.password();
        errorReported = false;
        device = DeviceState.UNKNOWN;
        temperature = null;
        humidity = null;
        lastUpdate = null;
        broker = ConnectionState.CONNECTING;
        message = "Connecting to " + settings.host() + ":" + settings.port();
        addLog(message);
        changed();

        var listener = new MqttTransport.Listener() {
            @Override
            public void onConnection(ConnectionState state) {
                onUi(gen, () -> handleConnection(state));
            }

            @Override
            public void onMessage(String topic, byte[] payload) {
                onUi(gen, () -> handleMessage(topic, payload));
            }

            @Override
            public void onError(String text) {
                onUi(gen, () -> handleError(text));
            }
        };
        try {
            transport.connect(settings, listener).whenComplete((ok, error) -> {
                if (error != null) {
                    onUi(gen, this::handleConnectFailure);
                }
            });
        } catch (RuntimeException ex) {
            handleConnectFailure();
        }
    }

    /** Ends the current session and ignores whatever it still reports. */
    public void disconnect() {
        if (closed.get()) {
            return;
        }
        generation.incrementAndGet();
        try {
            transport.disconnect().whenComplete((ok, error) -> { });
        } catch (RuntimeException ex) {
            // best effort; the state below is what the user sees
        }
        broker = ConnectionState.DISCONNECTED;
        device = DeviceState.UNKNOWN;
        message = "Disconnected";
        addLog(message);
        changed();
    }

    /** Publishes "ON" or "OFF" to sensor/led (QoS 0, not retained); refused while not connected. */
    public void setLedOn(boolean on) {
        String command = on ? "ON" : "OFF";
        if (closed.get() || broker != ConnectionState.CONNECTED || !transport.isConnected()) {
            message = "LED command refused: not connected";
            addLog(message);
            changed();
            return;
        }
        int gen = generation.get();
        try {
            transport.publish(TOPIC_LED, command.getBytes(StandardCharsets.UTF_8), 0, false)
                .whenComplete((ok, error) -> {
                    if (error != null) {
                        onUi(gen, () -> {
                            message = "LED " + command + " failed: broker is offline";
                            addLog(message);
                            changed();
                        });
                    }
                });
            addLog("LED " + command + " sent");
        } catch (RuntimeException ex) {
            message = "LED " + command + " failed";
            addLog(message);
        }
        changed();
    }

    /**
     * Idempotent. Sends DISCONNECT and then releases the transport. The returned stage completes
     * when that is done so a caller on a worker thread can wait briefly for it; later calls return
     * an already completed stage and do nothing.
     */
    public CompletionStage<Void> close() {
        if (closed.getAndSet(true)) {
            return CompletableFuture.completedFuture(null);
        }
        generation.incrementAndGet();
        var done = new CompletableFuture<Void>();
        try {
            transport.disconnect().whenComplete((ok, error) -> releaseTransport(done));
        } catch (RuntimeException ex) {
            releaseTransport(done);
        }
        return done;
    }

    private void releaseTransport(CompletableFuture<Void> done) {
        try {
            transport.close();
        } finally {
            done.complete(null);
        }
    }

    private void onUi(int gen, Runnable action) {
        ui.execute(() -> {
            if (gen == generation.get() && !closed.get()) {
                action.run();
            }
        });
    }

    private void handleConnection(ConnectionState state) {
        broker = state;
        switch (state) {
            case CONNECTED -> message = "Connected";
            case CONNECTING -> message = "Connecting";
            case RECONNECTING -> message = "Connection lost, reconnecting";
            case DISCONNECTED -> message = "Disconnected";
            case ERROR -> {
                if (!errorReported) {
                    message = "Connection error";
                }
            }
        }
        if (state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING) {
            device = DeviceState.UNKNOWN; // the retained status is re-delivered after a new subscribe
        }
        addLog("Broker: " + state);
        changed();
    }

    private void handleError(String text) {
        errorReported = true;
        message = redact(text == null ? "Unknown error" : text);
        addLog(message);
        changed();
    }

    private void handleConnectFailure() {
        broker = ConnectionState.ERROR;
        if (!errorReported) {
            message = "Connect failed";
            addLog(message);
        }
        changed();
    }

    private void handleMessage(String topic, byte[] payload) {
        switch (topic) {
            case TOPIC_DATA -> handleTelemetry(payload);
            case TOPIC_STATUS -> handleStatus(payload);
            default -> { } // sensor/led is our own command echoed back
        }
        changed();
    }

    private void handleTelemetry(byte[] payload) {
        // Check the size on the raw bytes first so an oversized payload is never decoded.
        if (payload.length > MAX_TELEMETRY_BYTES) {
            addLog("Rejected telemetry: payload larger than " + MAX_TELEMETRY_BYTES
                + " bytes (" + payload.length + ")");
            return;
        }
        String text = new String(payload, StandardCharsets.UTF_8);
        Matcher m = TEMP_FIRST.matcher(text);
        boolean tempFirst = m.matches();
        if (!tempFirst) {
            m = HUM_FIRST.matcher(text);
            if (!m.matches()) {
                addLog("Rejected telemetry: expected {\"temp\":<number>,\"hum\":<number>}");
                return;
            }
        }
        double t = Double.parseDouble(m.group(tempFirst ? 1 : 2));
        double h = Double.parseDouble(m.group(tempFirst ? 2 : 1));
        if (!(t >= MIN_TEMP && t <= MAX_TEMP) || !(h >= MIN_HUM && h <= MAX_HUM)) {
            addLog("Rejected telemetry: value out of range");
            return;
        }
        temperature = t;
        humidity = h;
        lastUpdate = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        addLog("Telemetry: temp=" + t + " hum=" + h);
    }

    private void handleStatus(byte[] payload) {
        if (payload.length <= MAX_STATUS_BYTES) {
            String text = new String(payload, StandardCharsets.UTF_8).trim();
            if (text.equals("ONLINE")) {
                device = DeviceState.ONLINE;
                addLog("Device: ONLINE");
                return;
            }
            if (text.equals("OFFLINE")) {
                device = DeviceState.OFFLINE;
                addLog("Device: OFFLINE");
                return;
            }
        }
        addLog("Ignored unknown status payload");
    }

    /** Transport texts never carry the password; this guards against one that ever does. */
    private String redact(String text) {
        return password.isEmpty() ? text : text.replace(password, "***");
    }

    private void addLog(String line) {
        while (log.size() >= MAX_LOG_LINES) {
            log.removeFirst();
        }
        log.addLast(LocalTime.now().format(CLOCK) + " " + line);
    }

    private void changed() {
        snapshot = buildSnapshot();
        onChange.accept(snapshot);
    }

    private Snapshot buildSnapshot() {
        return new Snapshot(broker, device, temperature, humidity, lastUpdate, message, List.copyOf(log));
    }
}
