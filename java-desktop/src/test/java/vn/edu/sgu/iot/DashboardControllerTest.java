package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;
import vn.edu.sgu.iot.ui.DashboardController;
import vn.edu.sgu.iot.ui.DashboardController.DeviceState;

class DashboardControllerTest {
    private static final String PASSWORD = "s3cret-pw";

    private final ConnectionSettings settings =
        new ConnectionSettings("192.168.43.100", 1883, "user1", PASSWORD);
    private final List<DashboardController.Snapshot> seen = new ArrayList<>();
    private FakeTransport transport;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        controller = new DashboardController(transport, Runnable::run);
        controller.setOnChange(seen::add);
    }

    private void data(String json) {
        transport.listener.onMessage("sensor/data", json.getBytes(StandardCharsets.UTF_8));
    }

    private void status(String text) {
        transport.listener.onMessage("sensor/status", text.getBytes(StandardCharsets.UTF_8));
    }

    private void assertTelemetryRejected(String json) {
        controller.connect(settings);
        data(json);
        var s = controller.snapshot();
        assertNull(s.temperature(), json);
        assertNull(s.humidity(), json);
        assertNull(s.lastUpdate(), json);
        assertTrue(s.log().get(s.log().size() - 1).contains("Rejected telemetry"), s.log().toString());
    }

    @Test
    void validTelemetryUpdatesReadings() {
        controller.connect(settings);
        data("{\"temp\":28.5,\"hum\":65}");
        var s = controller.snapshot();
        assertEquals(ConnectionState.CONNECTED, s.broker());
        assertEquals(28.5, s.temperature());
        assertEquals(65.0, s.humidity());
        assertNotNull(s.lastUpdate());
    }

    @Test
    void telemetryAcceptsEitherKeyOrderNegativeValuesAndWhitespace() {
        controller.connect(settings);
        data(" { \"hum\" : 40.5 , \"temp\" : -3.25 } \n");
        assertEquals(-3.25, controller.snapshot().temperature());
        assertEquals(40.5, controller.snapshot().humidity());
    }

    @Test
    void outOfRangeTemperatureIsRejected() {
        assertTelemetryRejected("{\"temp\":500,\"hum\":50}");
    }

    @Test
    void outOfRangeHumidityIsRejected() {
        assertTelemetryRejected("{\"temp\":20,\"hum\":150}");
        assertTelemetryRejected("{\"temp\":20,\"hum\":-1}");
    }

    @Test
    void rejectedTelemetryKeepsThePreviousReadings() {
        controller.connect(settings);
        data("{\"temp\":20,\"hum\":50}");
        data("{\"temp\":500,\"hum\":50}");
        assertEquals(20.0, controller.snapshot().temperature());
    }

    @Test
    void missingFieldIsRejected() {
        assertTelemetryRejected("{\"temp\":28.5}");
        assertTelemetryRejected("{\"hum\":65}");
        assertTelemetryRejected("{}");
    }

    @Test
    void nonNumericValuesAreRejected() {
        assertTelemetryRejected("{\"temp\":\"hot\",\"hum\":65}");
        assertTelemetryRejected("{\"temp\":NaN,\"hum\":65}");
        assertTelemetryRejected("{\"temp\":1e2,\"hum\":65}");
        assertTelemetryRejected("{\"temp\":null,\"hum\":65}");
        assertTelemetryRejected("not json");
    }

    @Test
    void duplicateKeysAreRejected() {
        assertTelemetryRejected("{\"temp\":20,\"temp\":21,\"hum\":50}");
        assertTelemetryRejected("{\"temp\":20,\"hum\":50,\"hum\":51}");
    }

    @Test
    void nestedOrStringEmbeddedKeysAreRejected() {
        assertTelemetryRejected("{\"x\":{\"temp\":20},\"hum\":50}");
        assertTelemetryRejected("{\"note\":\"\\\"temp\\\":20\",\"temp\":21,\"hum\":50}");
        assertTelemetryRejected("{\"note\":\"temp\",\"temp\":21,\"hum\":50}");
        assertTelemetryRejected("{\"temp\":20,\"hum\":50,\"extra\":{\"temp\":1}}");
    }

    @Test
    void payloadOverOneKilobyteIsRejectedWithoutDecoding() {
        controller.connect(settings);
        // 0xFF is not valid UTF-8; the rejection must come from the byte length alone.
        byte[] big = new byte[1025];
        Arrays.fill(big, (byte) 0xFF);
        transport.listener.onMessage("sensor/data", big);
        var s = controller.snapshot();
        assertNull(s.temperature());
        String last = s.log().get(s.log().size() - 1);
        assertTrue(last.contains("larger than 1024 bytes"), last);
        assertFalse(last.contains("�"), last);
    }

    @Test
    void payloadOfExactlyOneKilobyteIsStillAccepted() {
        controller.connect(settings);
        String json = "{\"temp\":20,\"hum\":50}";
        data(json + " ".repeat(1024 - json.length()));
        assertEquals(20.0, controller.snapshot().temperature());
    }

    @Test
    void statusOnlineAndOfflineUpdateTheDevice() {
        controller.connect(settings);
        assertEquals(DeviceState.UNKNOWN, controller.snapshot().device());
        status("ONLINE");
        assertEquals(DeviceState.ONLINE, controller.snapshot().device());
        status("OFFLINE");
        assertEquals(DeviceState.OFFLINE, controller.snapshot().device());
        status("banana");
        assertEquals(DeviceState.OFFLINE, controller.snapshot().device());
    }

    @Test
    void deviceBecomesUnknownWhenTheBrokerConnectionIsLost() {
        controller.connect(settings);
        status("ONLINE");
        transport.listener.onConnection(ConnectionState.RECONNECTING);
        assertEquals(DeviceState.UNKNOWN, controller.snapshot().device());
        assertEquals(ConnectionState.RECONNECTING, controller.snapshot().broker());
    }

    @Test
    void ledPublishIsRefusedWhenDisconnected() {
        controller.setLedOn(true);
        assertTrue(transport.sent.isEmpty());
        assertTrue(controller.snapshot().message().contains("not connected"));
        controller.connect(settings);
        controller.disconnect();
        controller.setLedOn(false);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void ledCommandsArePublishedWhenConnected() {
        controller.connect(settings);
        controller.setLedOn(true);
        controller.setLedOn(false);
        assertEquals(List.of("sensor/led:ON", "sensor/led:OFF"), transport.sent);
    }

    @Test
    void logIsCappedAtFiftyLines() {
        controller.connect(settings);
        for (int i = 0; i < 120; i++) {
            status(i % 2 == 0 ? "ONLINE" : "OFFLINE");
        }
        assertEquals(50, controller.snapshot().log().size());
        assertTrue(controller.snapshot().log().get(49).endsWith("Device: OFFLINE"));
    }

    @Test
    void passwordNeverAppearsInLogOrStatusText() {
        transport.failConnect = true;
        transport.errorMessage = "Authentication failed for " + PASSWORD;
        controller.connect(settings);
        transport.failConnect = false;
        controller.connect(settings);
        data("{\"temp\":20,\"hum\":50}");
        controller.setLedOn(true);
        controller.disconnect();
        assertFalse(seen.isEmpty());
        for (var s : seen) {
            assertFalse(s.message().contains(PASSWORD), s.message());
            for (String line : s.log()) {
                assertFalse(line.contains(PASSWORD), line);
            }
        }
    }

    @Test
    void failedConnectShowsErrorState() {
        transport.failConnect = true;
        controller.connect(settings);
        assertEquals(ConnectionState.ERROR, controller.snapshot().broker());
    }

    @Test
    void callbacksOfAnOlderSessionAreIgnored() {
        var listeners = new ArrayList<MqttTransport.Listener>();
        var pending = new FakeTransport() {
            @Override
            public CompletionStage<Void> connect(ConnectionSettings s, Listener l) {
                listeners.add(l);
                return new CompletableFuture<>();
            }
        };
        var c = new DashboardController(pending, Runnable::run);
        c.connect(settings);
        c.connect(settings);
        assertEquals(2, listeners.size());
        listeners.get(0).onConnection(ConnectionState.CONNECTED);
        listeners.get(0).onMessage("sensor/data", "{\"temp\":20,\"hum\":50}".getBytes(StandardCharsets.UTF_8));
        assertEquals(ConnectionState.CONNECTING, c.snapshot().broker());
        assertNull(c.snapshot().temperature());
        listeners.get(1).onConnection(ConnectionState.CONNECTED);
        assertEquals(ConnectionState.CONNECTED, c.snapshot().broker());
    }

    @Test
    void callbacksAreDeliveredThroughTheUiExecutor() {
        var queued = new ArrayList<Runnable>();
        var c = new DashboardController(transport, queued::add);
        c.connect(settings); // FakeTransport reports CONNECTED synchronously
        assertEquals(ConnectionState.CONNECTING, c.snapshot().broker());
        queued.forEach(Runnable::run);
        assertEquals(ConnectionState.CONNECTED, c.snapshot().broker());
    }

    @Test
    void closeIsIdempotent() {
        controller.connect(settings);
        controller.close();
        assertEquals(1, transport.disconnectCalls);
        assertTrue(transport.closed);
        transport.closed = false;
        controller.close();
        assertEquals(1, transport.disconnectCalls);
        assertFalse(transport.closed);
    }

    @Test
    void connectAfterCloseDoesNothing() {
        controller.close();
        controller.connect(settings);
        assertNull(transport.listener);
    }
}
