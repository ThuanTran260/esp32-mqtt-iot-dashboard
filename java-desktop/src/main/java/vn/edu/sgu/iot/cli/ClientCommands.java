package vn.edu.sgu.iot.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;

/** Runs one CLI mode against a transport. Never calls {@code System.exit}; returns the exit code. */
public final class ClientCommands {
    public static final String DASHBOARD_UNAVAILABLE =
        "Dashboard UI is not available yet; use check/subscribe/publish";

    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;
    private static final long DISCONNECT_TIMEOUT_SECONDS = 3;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ClientCommands() {
    }

    public static int run(ClientOptions options, MqttTransport transport, PrintStream out, PrintStream err) {
        return run(options, transport, out, err, new CountDownLatch(1));
    }

    /**
     * @param stop counting it down ends {@code subscribe} early (still exit 0), e.g. on Ctrl+C
     * @return 0 on success, 1 on a connection, config or publish failure, 2 for an unusable mode
     */
    public static int run(ClientOptions options, MqttTransport transport, PrintStream out,
                          PrintStream err, CountDownLatch stop) {
        if (options.mode().equals("dashboard")) {
            err.println(DASHBOARD_UNAVAILABLE);
            return 2;
        }
        ConnectionSettings settings;
        byte[] payload = null;
        try {
            settings = ConnectionSettings.load(options.config());
            if (options.mode().equals("publish")) {
                payload = options.payload();
            }
        } catch (NoSuchFileException ex) {
            err.println("File not found: " + ex.getFile());
            return 1;
        } catch (IOException ex) {
            err.println(oneLine(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return 1;
        }

        boolean subscribing = options.mode().equals("subscribe");
        var lastError = new AtomicReference<String>();
        var connected = new AtomicBoolean();
        var listener = new MqttTransport.Listener() {
            @Override
            public void onConnection(ConnectionState state) {
                // The first CONNECTED is reported by the mode itself; later changes (broker died, came
                // back) would otherwise leave a subscriber silent until --seconds runs out. A failed
                // reconnect (ERROR) is reported through onError.
                if (state == ConnectionState.CONNECTED) {
                    if (connected.getAndSet(true)) {
                        err.println("Reconnected to " + settings.host() + ":" + settings.port());
                    }
                } else if (state == ConnectionState.RECONNECTING && connected.get()) {
                    err.println("Connection lost, reconnecting to " + settings.host() + ":" + settings.port());
                }
            }

            @Override
            public void onMessage(String topic, byte[] body) {
                if (subscribing) {
                    out.println(LocalTime.now().format(CLOCK) + " " + topic + " "
                        + oneLine(new String(body, StandardCharsets.UTF_8)));
                }
            }

            @Override
            public void onError(String message) {
                lastError.set(message);
                if (connected.get()) {
                    err.println(friendly(message, settings)); // e.g. connection lost while subscribed
                }
            }
        };

        try {
            try {
                transport.connect(settings, listener).toCompletableFuture()
                    .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException ex) {
                err.println(friendly(lastError.get(), settings));
                return 1;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                err.println("Interrupted");
                return 1;
            }
            return switch (options.mode()) {
                case "check" -> {
                    out.println("CONNECTED to " + settings.host() + ":" + settings.port());
                    yield 0;
                }
                case "publish" -> publish(options, transport, payload, err);
                case "subscribe" -> {
                    err.println("Listening on " + options.topic() + " for " + options.seconds() + " s");
                    awaitStop(stop, options.seconds());
                    yield 0;
                }
                default -> {
                    err.println("Unknown mode: " + options.mode());
                    yield 2;
                }
            };
        } catch (RuntimeException ex) {
            err.println("Unexpected error: " + ex.getClass().getSimpleName());
            return 1;
        } finally {
            shutdown(transport);
        }
    }

    private static int publish(ClientOptions options, MqttTransport transport, byte[] payload,
                               PrintStream err) {
        try {
            transport.publish(options.topic(), payload, 0, false).toCompletableFuture()
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return 0;
        } catch (ExecutionException | TimeoutException ex) {
            err.println("Publish failed: " + (ex instanceof TimeoutException ? "timed out" : "broker is offline"));
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("Publish interrupted");
            return 1;
        }
    }

    private static void awaitStop(CountDownLatch stop, int seconds) {
        try {
            stop.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Sends DISCONNECT (so the broker does not publish a Will) and releases the transport. */
    private static void shutdown(MqttTransport transport) {
        try {
            transport.disconnect().toCompletableFuture().get(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            // best effort; the process is about to end
        } finally {
            transport.close();
        }
    }

    /** Maps the transport's reason onto the CLI wording; never mentions credentials. */
    private static String friendly(String reported, ConnectionSettings settings) {
        String unreachable = "Cannot reach broker at " + settings.host() + ":" + settings.port();
        if (reported == null || reported.startsWith("Timed out connecting")
            || reported.startsWith("Cannot reach broker")) {
            return unreachable;
        }
        if (reported.startsWith("Authentication failed")) {
            return "Authentication failed";
        }
        return reported;
    }

    private static String oneLine(String text) {
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }
}
