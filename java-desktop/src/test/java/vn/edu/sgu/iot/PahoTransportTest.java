package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;
import vn.edu.sgu.iot.mqtt.PahoTransport;

class PahoTransportTest {

    /** Collects callbacks into queues so assertions run on the test thread. */
    private static final class Recorder implements MqttTransport.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        final BlockingQueue<ConnectionState> states = new LinkedBlockingQueue<>();

        @Override
        public void onConnection(ConnectionState state) {
            states.add(state);
        }

        @Override
        public void onMessage(String topic, byte[] bytes) {
            messages.add(topic + ":" + new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void onError(String message) {
            errors.add(message);
        }

        /** Waits for a state to appear, discarding earlier ones. */
        boolean awaitState(ConnectionState wanted, long seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (true) {
                long left = deadline - System.nanoTime();
                if (left <= 0) return false;
                ConnectionState s = states.poll(left, TimeUnit.NANOSECONDS);
                if (s == wanted) return true;
            }
        }
    }

    /** A plain Paho client acting as the sensor/observer; closes cleanly (disconnect, then close). */
    private static final class Source implements AutoCloseable {
        private final MqttClient client;

        Source(LocalBrokerFixture fixture, String id) throws Exception {
            client = new MqttClient(fixture.uri(), id, new MemoryPersistence());
            var options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(LocalBrokerFixture.USER);
            options.setPassword(LocalBrokerFixture.PASSWORD.toCharArray());
            client.connect(options);
        }

        void publish(String topic, String payload) throws Exception {
            client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0, false);
        }

        void subscribe(String topic, BlockingQueue<String> into) throws Exception {
            client.subscribe(topic, 0, (t, m) ->
                into.add(t + ":" + new String(m.getPayload(), StandardCharsets.UTF_8)));
        }

        @Override
        public void close() throws Exception {
            if (client.isConnected()) client.disconnect();
            client.close();
        }
    }

    /** Names of live Paho threads that belong to a transport client (they carry the client id). */
    private static List<String> transportThreads() {
        return Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("MQTT ") && name.contains("java-ui-"))
            .toList();
    }

    /** Accepts TCP connections and never answers: a broker that hangs before CONNACK. */
    private static final class SilentServer implements AutoCloseable {
        private final ServerSocket server;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        SilentServer(int port) throws IOException {
            server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            var thread = new Thread(() -> {
                try {
                    while (true) accepted.add(server.accept());
                } catch (IOException ignored) {
                    // closed
                }
            }, "silent-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        /** Stops listening but keeps already accepted sockets open (and silent). */
        void stopAccepting() throws IOException {
            server.close();
        }

        @Override
        public void close() throws IOException {
            server.close();
            for (Socket socket : accepted) socket.close();
        }
    }

    private static boolean awaitNoTransportThreads(long seconds) throws InterruptedException {
        await(() -> transportThreads().isEmpty(), seconds);
        return transportThreads().isEmpty();
    }

    private static void await(BooleanSupplier condition, long seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
    }

    private static Throwable failureOf(CompletableFuture<Void> future, long seconds) {
        var ex = assertThrows(ExecutionException.class, () -> future.get(seconds, TimeUnit.SECONDS));
        return ex.getCause();
    }

    @Test
    void receivesTelemetryThroughJavaBroker() throws Exception {
        try (var fixture = LocalBrokerFixture.open();
             var transport = new PahoTransport();
             var source = new Source(fixture, "source")) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertTrue(transport.isConnected());
            source.publish("sensor/data", "{\"temp\":28.5,\"hum\":65.0}");
            assertEquals("sensor/data:{\"temp\":28.5,\"hum\":65.0}", listener.messages.poll(3, TimeUnit.SECONDS));
            assertTrue(listener.errors.isEmpty());
            assertEquals(ConnectionState.CONNECTING, listener.states.poll());
            assertEquals(ConnectionState.CONNECTED, listener.states.poll());
        }
    }

    @Test
    void defaultConstructorSubscribesToAllThreeStandardTopics() throws Exception {
        try (var fixture = LocalBrokerFixture.open();
             var transport = new PahoTransport();
             var source = new Source(fixture, "source")) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            source.publish("sensor/led", "ON");
            source.publish("sensor/status", "ONLINE");
            source.publish("other/topic", "ignored");
            assertEquals("sensor/led:ON", listener.messages.poll(3, TimeUnit.SECONDS));
            assertEquals("sensor/status:ONLINE", listener.messages.poll(3, TimeUnit.SECONDS));
            assertNull(listener.messages.poll(300, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void customSubscriptionsReplaceTheStandardOnes() throws Exception {
        try (var fixture = LocalBrokerFixture.open();
             var transport = new PahoTransport(Map.of("custom/+", 0));
             var source = new Source(fixture, "source")) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            source.publish("sensor/data", "not-subscribed");
            source.publish("custom/data", "hello");
            assertEquals("custom/data:hello", listener.messages.poll(3, TimeUnit.SECONDS));
            assertNull(listener.messages.poll(300, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void rejectsInvalidStartupSubscriptions() {
        assertThrows(IllegalArgumentException.class, () -> new PahoTransport(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PahoTransport(Map.of("a/b", 2)));
        assertThrows(NullPointerException.class, () -> new PahoTransport(null));
    }

    @Test
    void wrongPasswordFailsWithoutLeakingIt() throws Exception {
        String wrong = "not-the-password-42";
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            var settings = new ConnectionSettings("127.0.0.1", fixture.port(), LocalBrokerFixture.USER, wrong);
            var future = transport.connect(settings, listener).toCompletableFuture();
            Throwable cause = failureOf(future, 6);
            assertFalse(String.valueOf(cause.getMessage()).contains(wrong));
            assertFalse(transport.isConnected());
            String error = listener.errors.poll(2, TimeUnit.SECONDS);
            assertNotNull(error);
            assertTrue(error.toLowerCase().contains("authentication failed"), error);
            assertFalse(error.contains(wrong));
            for (String e : listener.errors) assertFalse(e.contains(wrong));
            assertTrue(listener.awaitState(ConnectionState.ERROR, 2));
            // No auth retry loop: nothing more happens.
            assertNull(listener.states.poll(1500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void hostWithoutBrokerFailsWithinSixSeconds() throws Exception {
        int freePort;
        try (var probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        try (var transport = new PahoTransport()) {
            var listener = new Recorder();
            var settings = new ConnectionSettings("127.0.0.1", freePort, "user1", "123456");
            long start = System.nanoTime();
            var future = transport.connect(settings, listener).toCompletableFuture();
            failureOf(future, 6);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) <= 6000);
            assertFalse(transport.isConnected());
            assertTrue(listener.awaitState(ConnectionState.ERROR, 2));
            assertFalse(listener.errors.isEmpty());
        }
    }

    /**
     * Accepts connections and closes each after a pause: something that listens but is not an MQTT
     * broker. The pause matters: closing at once (or after a few hundred ms) can leave Paho 1.2.5's
     * connect thread spinning in {@code CommsReceiver.start}, a daemon thread that would trip the
     * thread-leak assertions of the other tests in this JVM.
     */
    @Test
    void serverThatClosesTheSocketGivesReadableError() throws Exception {
        try (var server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             var transport = new PahoTransport()) {
            var acceptor = new Thread(() -> {
                try {
                    while (true) {
                        Socket socket = server.accept();
                        Thread.sleep(1000);
                        socket.close();
                    }
                } catch (IOException | InterruptedException ignored) {
                    // closed
                }
            }, "closing-server");
            acceptor.setDaemon(true);
            acceptor.start();
            var listener = new Recorder();
            var settings = new ConnectionSettings("127.0.0.1", server.getLocalPort(), "user1", "123456");
            failureOf(transport.connect(settings, listener).toCompletableFuture(), 6);
            String error = listener.errors.poll(2, TimeUnit.SECONDS);
            assertNotNull(error);
            assertEquals("Connection closed by broker at 127.0.0.1:" + server.getLocalPort()
                + " (is this an MQTT broker?)", error);
            assertFalse(error.contains("reason code"), error);
            assertTrue(awaitNoTransportThreads(3), "Paho client must be shut down: " + transportThreads());
        }
    }

    @Test
    void publishDeliversAndFailsWhenOffline() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var received = new LinkedBlockingQueue<String>();
            var offline = transport.publish("sensor/led", new byte[] {'O', 'N'}, 0, false).toCompletableFuture();
            var cause = failureOf(offline, 1);
            assertInstanceOf(IllegalStateException.class, cause);
            assertEquals("Broker disconnected", cause.getMessage());

            try (var observer = new Source(fixture, "observer")) {
                observer.subscribe("sensor/led", received);
                transport.connect(fixture.settings(), new Recorder()).toCompletableFuture().get(3, TimeUnit.SECONDS);
                transport.publish("sensor/led", "ON".getBytes(StandardCharsets.UTF_8), 0, false)
                    .toCompletableFuture().get(3, TimeUnit.SECONDS);
                assertEquals("sensor/led:ON", received.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void reconnectsAndResubscribesAfterBrokerRestart() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            fixture.stop();
            assertTrue(listener.awaitState(ConnectionState.RECONNECTING, 5));
            assertFalse(transport.isConnected());
            fixture.start();
            assertTrue(listener.awaitState(ConnectionState.CONNECTED, 15), "should reconnect");
            try (var source = new Source(fixture, "source")) {
                source.publish("sensor/data", "{\"temp\":1.0,\"hum\":2.0}");
                assertEquals("sensor/data:{\"temp\":1.0,\"hum\":2.0}", listener.messages.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void explicitDisconnectDoesNotReconnect() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(transport.isConnected());
            await(() -> fixture.server().router().subscriberCount() == 0, 3);
            assertEquals(0, fixture.server().router().subscriberCount());

            fixture.stop();
            fixture.start();
            Thread.sleep(2500);
            assertEquals(0, fixture.server().router().subscriberCount(), "must not reconnect");
            assertEquals(List.of(), transportThreads(), "Paho client must be shut down");

            List<ConnectionState> states = new ArrayList<>();
            listener.states.drainTo(states);
            assertEquals(
                List.of(ConnectionState.CONNECTING, ConnectionState.CONNECTED, ConnectionState.DISCONNECTED), states);
            // A second disconnect is a harmless no-op.
            transport.disconnect().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(listener.states.isEmpty());
        }
    }

    @Test
    void disconnectWhileReconnectingCancelsTheRetry() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            fixture.stop();
            assertTrue(listener.awaitState(ConnectionState.RECONNECTING, 5));
            transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
            fixture.start();
            Thread.sleep(4000);
            assertEquals(0, fixture.server().router().subscriberCount(), "must not reconnect");
            assertEquals(List.of(), transportThreads(), "no reconnect: Paho client must be shut down");
            assertFalse(transport.isConnected());
        }
    }

    @Test
    void serverThatNeverSendsConnackFailsWithinSixSeconds() throws Exception {
        try (var silent = new SilentServer(0); var transport = new PahoTransport()) {
            var listener = new Recorder();
            var settings = new ConnectionSettings("127.0.0.1", silent.port(), "user1", "123456");
            long start = System.nanoTime();
            var future = transport.connect(settings, listener).toCompletableFuture();
            failureOf(future, 7);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) <= 6000, "connect must give up in 6 s");
            assertFalse(transport.isConnected());
            assertTrue(listener.awaitState(ConnectionState.ERROR, 2));
            String error = listener.errors.poll(2, TimeUnit.SECONDS);
            assertNotNull(error);
            assertTrue(error.toLowerCase().contains("timed out"), error);
            assertTrue(awaitNoTransportThreads(3), "Paho client must be shut down: " + transportThreads());
        }
    }

    @Test
    void disconnectDuringInFlightConnectLeavesNoThreads() throws Exception {
        try (var silent = new SilentServer(0); var transport = new PahoTransport()) {
            var settings = new ConnectionSettings("127.0.0.1", silent.port(), "user1", "123456");
            var future = transport.connect(settings, new Recorder()).toCompletableFuture();
            Thread.sleep(500);
            transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(future.isDone());
            assertTrue(awaitNoTransportThreads(3), "leaked: " + transportThreads());
        }
    }

    @Test
    void closeDuringInFlightConnectLeavesNoThreads() throws Exception {
        try (var silent = new SilentServer(0)) {
            var transport = new PahoTransport();
            var settings = new ConnectionSettings("127.0.0.1", silent.port(), "user1", "123456");
            transport.connect(settings, new Recorder());
            Thread.sleep(500);
            transport.close();
            assertTrue(awaitNoTransportThreads(3), "leaked: " + transportThreads());
        }
    }

    @Test
    void disconnectDuringInFlightReconnectLeavesNoThreads() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            fixture.stop();
            try (var silent = new SilentServer(fixture.port())) {
                assertTrue(listener.awaitState(ConnectionState.RECONNECTING, 5));
                Thread.sleep(2000); // the 1 s retry is now stuck waiting for a CONNACK
                transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertTrue(awaitNoTransportThreads(3), "leaked: " + transportThreads());
            }
        }
    }

    @Test
    void reconnectAttemptStuckWithoutConnackIsAbortedAndRetried() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            var listener = new Recorder();
            transport.connect(fixture.settings(), listener).toCompletableFuture().get(3, TimeUnit.SECONDS);
            fixture.stop();
            try (var silent = new SilentServer(fixture.port())) {
                assertTrue(listener.awaitState(ConnectionState.RECONNECTING, 5));
                Thread.sleep(7000); // longer than the per-attempt deadline; accepted sockets stay silent
                silent.stopAccepting();
                fixture.start();
                assertTrue(listener.awaitState(ConnectionState.CONNECTED, 15), "should recover once a broker is back");
            }
        }
    }

    @Test
    void canConnectAgainAfterDisconnect() throws Exception {
        try (var fixture = LocalBrokerFixture.open(); var transport = new PahoTransport()) {
            transport.connect(fixture.settings(), new Recorder()).toCompletableFuture().get(3, TimeUnit.SECONDS);
            transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
            var second = new Recorder();
            transport.connect(fixture.settings(), second).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertTrue(transport.isConnected());
            try (var source = new Source(fixture, "source")) {
                source.publish("sensor/status", "ONLINE");
                assertEquals("sensor/status:ONLINE", second.messages.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void closeIsIdempotentAndBlocksFurtherUse() throws Exception {
        try (var fixture = LocalBrokerFixture.open()) {
            var transport = new PahoTransport();
            transport.connect(fixture.settings(), new Recorder()).toCompletableFuture().get(3, TimeUnit.SECONDS);
            transport.close();
            transport.close();
            assertFalse(transport.isConnected());
            await(() -> fixture.server().router().subscriberCount() == 0, 3);
            assertEquals(0, fixture.server().router().subscriberCount());
            failureOf(transport.connect(fixture.settings(), new Recorder()).toCompletableFuture(), 1);
        }
    }

    @Test
    void twoTransportsUseDifferentClientIds() throws Exception {
        // The broker takes over a session when a client id repeats, so equal ids would kick the first one.
        try (var fixture = LocalBrokerFixture.open();
             var a = new PahoTransport();
             var b = new PahoTransport();
             var source = new Source(fixture, "source")) {
            var la = new Recorder();
            var lb = new Recorder();
            a.connect(fixture.settings(), la).toCompletableFuture().get(3, TimeUnit.SECONDS);
            b.connect(fixture.settings(), lb).toCompletableFuture().get(3, TimeUnit.SECONDS);
            source.publish("sensor/data", "{\"temp\":3.0,\"hum\":4.0}");
            assertEquals("sensor/data:{\"temp\":3.0,\"hum\":4.0}", la.messages.poll(3, TimeUnit.SECONDS));
            assertEquals("sensor/data:{\"temp\":3.0,\"hum\":4.0}", lb.messages.poll(3, TimeUnit.SECONDS));
            assertTrue(a.isConnected() && b.isConnected());
        }
    }
}
