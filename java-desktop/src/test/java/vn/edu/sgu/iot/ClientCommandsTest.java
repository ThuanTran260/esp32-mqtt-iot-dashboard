package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.sgu.iot.cli.ClientCommands;
import vn.edu.sgu.iot.cli.ClientOptions;
import vn.edu.sgu.iot.mqtt.ConnectionState;

class ClientCommandsTest {
    @TempDir
    Path dir;

    private Path config;
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void writeConfig() throws IOException {
        config = dir.resolve("client.properties");
        Files.writeString(config, "host=192.168.43.100\nport=1883\nusername=user1\npassword=s3cret\n");
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    /** {@code mode --config <config> extra...} */
    private ClientOptions options(String mode, String... extra) {
        var args = new ArrayList<String>(List.of(mode, "--config", config.toString()));
        args.addAll(Arrays.asList(extra));
        return ClientOptions.parse(args.toArray(String[]::new));
    }

    @Test
    void checkOnlineExitsZeroAndPrintsConnected() {
        var transport = new FakeTransport();
        int code = ClientCommands.run(options("check"), transport, out, err);
        assertEquals(0, code);
        assertEquals("CONNECTED to 192.168.43.100:1883", out().strip());
        assertEquals("", err());
        assertEquals(1, transport.disconnectCalls);
        assertTrue(transport.closed);
    }

    @Test
    void checkWhenConnectFailsExitsOneWithUnreachableMessage() {
        var transport = new FakeTransport();
        transport.failConnect = true;
        int code = ClientCommands.run(options("check"), transport, out, err);
        assertEquals(1, code);
        assertEquals("Cannot reach broker at 192.168.43.100:1883", err().strip());
        assertEquals("", out());
        assertTrue(transport.closed);
    }

    @Test
    void timeoutFromTransportIsReportedAsUnreachable() {
        var transport = new FakeTransport();
        transport.failConnect = true;
        transport.errorMessage = "Timed out connecting to 192.168.43.100:1883";
        assertEquals(1, ClientCommands.run(options("check"), transport, out, err));
        assertEquals("Cannot reach broker at 192.168.43.100:1883", err().strip());
    }

    @Test
    void authenticationFailureIsReportedWithoutPassword() {
        var transport = new FakeTransport();
        transport.failConnect = true;
        transport.errorMessage = "Authentication failed: check the username and password";
        assertEquals(1, ClientCommands.run(options("check"), transport, out, err));
        assertEquals("Authentication failed", err().strip());
        assertFalse(err().contains("s3cret"));
        assertFalse(out().contains("s3cret"));
    }

    @Test
    void connectionClosedByBrokerIsReportedReadablyWithExitOne() {
        var transport = new FakeTransport();
        transport.failConnect = true;
        transport.errorMessage =
            "Connection closed by broker at 192.168.43.100:1883 (is this an MQTT broker?)";
        assertEquals(1, ClientCommands.run(options("check"), transport, out, err));
        assertEquals("Connection closed by broker at 192.168.43.100:1883 (is this an MQTT broker?)",
            err().strip());
        assertEquals("", out());
    }

    @Test
    void missingConfigExitsOneWithSingleLineAndNoConnect() {
        var transport = new FakeTransport();
        var opts = ClientOptions.parse(
            new String[]{"check", "--config", dir.resolve("none.properties").toString()});
        assertEquals(1, ClientCommands.run(opts, transport, out, err));
        assertEquals(1, err().strip().lines().count());
        assertTrue(err().contains("none.properties"));
        assertNull(transport.listener);
    }

    @Test
    void invalidConfigExitsOneWithoutPassword() throws IOException {
        Files.writeString(config, "host=1.2.3.4\nport=abc\npassword=s3cret\n");
        var transport = new FakeTransport();
        assertEquals(1, ClientCommands.run(options("check"), transport, out, err));
        assertEquals(1, err().strip().lines().count());
        assertFalse(err().contains("s3cret"));
        assertNull(transport.listener);
    }

    @Test
    void publishSendsExactlyOneMessageToTheTopic() {
        var transport = new FakeTransport();
        int code = ClientCommands.run(
            options("publish", "--topic", "sensor/led", "--message", "ON"), transport, out, err);
        assertEquals(0, code);
        assertEquals(List.of("sensor/led:ON"), transport.sent);
        assertEquals(1, transport.disconnectCalls);
        assertTrue(transport.closed);
    }

    @Test
    void publishReadsMessageFile() throws IOException {
        Path file = dir.resolve("msg.txt");
        Files.writeString(file, "OFF");
        var transport = new FakeTransport();
        int code = ClientCommands.run(
            options("publish", "--topic", "sensor/led", "--message-file", file.toString()),
            transport, out, err);
        assertEquals(0, code);
        assertEquals(List.of("sensor/led:OFF"), transport.sent);
    }

    @Test
    void publishWithUnreadablePayloadFileExitsOneBeforeConnecting() {
        var transport = new FakeTransport();
        int code = ClientCommands.run(
            options("publish", "--topic", "t", "--message-file", dir.resolve("none.txt").toString()),
            transport, out, err);
        assertEquals(1, code);
        assertNull(transport.listener);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void publishFailureExitsOne() {
        var transport = new FakeTransport() {
            @Override
            public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }
        };
        int code = ClientCommands.run(
            options("publish", "--topic", "t", "--message", "x"), transport, out, err);
        assertEquals(1, code);
        assertTrue(err().contains("Publish failed"));
    }

    @Test
    void subscribeEndsAfterTimeoutWithExitZero() {
        var transport = new FakeTransport();
        long start = System.nanoTime();
        int code = ClientCommands.run(options("subscribe", "--seconds", "1"), transport, out, err);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(0, code);
        assertTrue(elapsedMillis >= 900 && elapsedMillis <= 3000, "elapsed " + elapsedMillis);
        assertEquals(1, transport.disconnectCalls);
        assertTrue(transport.closed);
    }

    @Test
    void subscribePrintsEachMessageOnOneLine() throws Exception {
        var transport = new FakeTransport();
        var stop = new CountDownLatch(1);
        var thread = new Thread(() -> {
            while (transport.listener == null) {
                Thread.onSpinWait();
            }
            transport.listener.onMessage("sensor/data", "{\"temp\":28.5}".getBytes(StandardCharsets.UTF_8));
            transport.listener.onMessage("sensor/status", "line1\nline2".getBytes(StandardCharsets.UTF_8));
            stop.countDown();
        });
        thread.start();
        int code = ClientCommands.run(options("subscribe", "--seconds", "5"), transport, out, err, stop);
        thread.join();
        assertEquals(0, code);
        var lines = out().strip().lines().toList();
        assertEquals(2, lines.size(), out());
        assertTrue(lines.get(0).matches("\\d\\d:\\d\\d:\\d\\d sensor/data \\{\"temp\":28\\.5}"), lines.get(0));
        assertTrue(lines.get(1).matches("\\d\\d:\\d\\d:\\d\\d sensor/status line1\\\\nline2"), lines.get(1));
    }

    @Test
    void subscribePrintsConnectionStateChangesToStderr() throws Exception {
        var transport = new FakeTransport();
        var stop = new CountDownLatch(1);
        var thread = new Thread(() -> {
            while (transport.listener == null) {
                Thread.onSpinWait();
            }
            transport.listener.onConnection(ConnectionState.RECONNECTING);
            transport.listener.onConnection(ConnectionState.CONNECTED);
            transport.listener.onConnection(ConnectionState.RECONNECTING);
            transport.listener.onError("Cannot reach broker at 192.168.43.100:1883");
            transport.listener.onConnection(ConnectionState.ERROR);
            stop.countDown();
        });
        thread.start();
        int code = ClientCommands.run(options("subscribe", "--seconds", "5"), transport, out, err, stop);
        thread.join();
        assertEquals(0, code);
        var lines = err().strip().lines().toList();
        assertEquals(List.of(
            "Listening on sensor/# for 5 s",
            "Connection lost, reconnecting to 192.168.43.100:1883",
            "Reconnected to 192.168.43.100:1883",
            "Connection lost, reconnecting to 192.168.43.100:1883",
            "Cannot reach broker at 192.168.43.100:1883"), lines);
        assertEquals("", out());
    }

    @Test
    void checkDoesNotPrintStateLinesOnSuccess() {
        var transport = new FakeTransport();
        ClientCommands.run(options("check"), transport, out, err);
        assertEquals("", err());
    }

    @Test
    void stopSignalEndsSubscribeEarlyWithExitZero() {
        var transport = new FakeTransport();
        var stop = new CountDownLatch(1);
        stop.countDown();
        long start = System.nanoTime();
        int code = ClientCommands.run(options("subscribe", "--seconds", "3600"), transport, out, err, stop);
        assertEquals(0, code);
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000);
        assertEquals(1, transport.disconnectCalls);
    }

    @Test
    void messagesAreNotPrintedInCheckMode() {
        var transport = new FakeTransport();
        ClientCommands.run(options("check"), transport, out, err);
        transport.listener.onMessage("sensor/data", "x".getBytes(StandardCharsets.UTF_8));
        assertFalse(out().contains("sensor/data"));
    }

    @Test
    void dashboardModeIsRejectedWithAClearMessage() {
        var transport = new FakeTransport();
        int code = ClientCommands.run(ClientOptions.parse(new String[]{"dashboard"}), transport, out, err);
        assertEquals(2, code);
        assertEquals("Dashboard mode is started by ClientMain, not by ClientCommands", err().strip());
        assertNull(transport.listener);
    }
}
