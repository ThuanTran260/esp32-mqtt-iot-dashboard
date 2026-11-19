package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.Packet;

@Timeout(30)
class BrokerConnectionTest {
    private static final int NO_CREDENTIALS_CLEAN = 0x02;
    private static final int CLEAN_WILL_QOS2_WITH_CREDENTIALS = 0xC2 | 0x04 | 0x10;

    private RunningBroker running;

    @BeforeEach
    void open() throws IOException {
        running = RunningBroker.open();
    }

    @AfterEach
    void close() {
        running.close();
    }

    @Test
    void acceptsValidCredentialsAndRejectsWrongPassword() throws Exception {
        try (var good = new MqttClient(running.uri(), "good", new MemoryPersistence());
             var bad = new MqttClient(running.uri(), "bad", new MemoryPersistence())) {
            var options = new MqttConnectOptions();
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setCleanSession(true);
            options.setUserName("user1");
            options.setPassword("123456".toCharArray());
            good.connect(options);
            assertTrue(good.isConnected());
            good.disconnect();
            options.setPassword("wrong".toCharArray());
            var error = assertThrows(MqttException.class, () -> bad.connect(options));
            assertEquals(4, error.getReasonCode());
        }
    }

    @Test
    void wrongPasswordOnRawSocketGetsCode4ThenCloses() throws Exception {
        try (var client = new RawClient(running.port())) {
            int code = client.connectAndGetCode(RawClient.connect(
                4, RawClient.CLEAN_WITH_CREDENTIALS, 60, "c", "user1", "nope"));
            assertEquals(4, code);
            client.assertClosed();
        }
    }

    @Test
    void wrongUsernameGetsCode4() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(4, client.connectAndGetCode(RawClient.connect(
                4, RawClient.CLEAN_WITH_CREDENTIALS, 60, "c", "other", "123456")));
            client.assertClosed();
        }
    }

    @Test
    void missingCredentialsGetCode4() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(4, client.connectAndGetCode(
                RawClient.connect(4, NO_CREDENTIALS_CLEAN, 60, "c", null, null)));
            client.assertClosed();
        }
    }

    @Test
    void protocolLevel5GetsCode1ThenCloses() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(1, client.connectAndGetCode(RawClient.connect(
                5, RawClient.CLEAN_WITH_CREDENTIALS, 60, "c", "user1", "123456")));
            client.assertClosed();
        }
    }

    @Test
    void emptyClientIdGetsCode2() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(2, client.connectAndGetCode(RawClient.validConnect("", 60)));
            client.assertClosed();
        }
    }

    @Test
    void cleanSessionFalseGetsCode2() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(2, client.connectAndGetCode(RawClient.connect(
                4, 0xC0, 60, "persistent", "user1", "123456")));
            client.assertClosed();
        }
    }

    @Test
    void willQos2GetsCode3() throws Exception {
        // flags: will flag + QoS 2 requires will topic/message fields after the client id.
        var body = new java.io.ByteArrayOutputStream();
        var data = new java.io.DataOutputStream(body);
        data.writeUTF("MQTT");
        data.writeByte(4);
        data.writeByte(CLEAN_WILL_QOS2_WITH_CREDENTIALS);
        data.writeShort(60);
        data.writeUTF("w");
        data.writeUTF("sensor/status");
        data.writeShort(1);
        data.writeByte('x');
        data.writeUTF("user1");
        data.writeUTF("123456");
        try (var client = new RawClient(running.port())) {
            assertEquals(3, client.connectAndGetCode(
                new Packet(MqttCodec.CONNECT, 0, body.toByteArray())));
            client.assertClosed();
        }
    }

    @Test
    void validRawConnectGetsCode0AndPingGetsPingresp() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("ping", 60)));
            client.send(new Packet(MqttCodec.PINGREQ, 0, new byte[0]));
            Packet response = client.read();
            assertNotNull(response);
            assertEquals(MqttCodec.PINGRESP, response.type());
            assertArrayEquals(new byte[0], response.body());
        }
    }

    @Test
    void packetBeforeConnectClosesConnection() throws Exception {
        try (var client = new RawClient(running.port())) {
            client.send(new Packet(MqttCodec.PINGREQ, 0, new byte[0]));
            client.assertClosed();
        }
    }

    @Test
    void secondConnectClosesConnection() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("twice", 60)));
            client.send(RawClient.validConnect("twice", 60));
            client.assertClosed();
        }
    }

    @Test
    void malformedPingreqClosesConnection() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("bad-ping", 60)));
            client.send(new Packet(MqttCodec.PINGREQ, 0, new byte[] {1}));
            client.assertClosed();
        }
    }

    @Test
    void packetTypeAClientMayNeverSendClosesConnection() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("odd", 60)));
            client.send(new Packet(MqttCodec.CONNACK, 0, new byte[] {0, 0}));
            client.assertClosed();
        }
    }

    @Test
    void duplicateClientIdDropsTheOldConnectionAndKeepsTheNewOne() throws Exception {
        try (var first = new RawClient(running.port());
             var second = new RawClient(running.port())) {
            assertEquals(0, first.connectAndGetCode(RawClient.validConnect("dup", 60)));
            assertEquals(0, second.connectAndGetCode(RawClient.validConnect("dup", 60)));
            first.assertClosed();
            second.send(new Packet(MqttCodec.PINGREQ, 0, new byte[0]));
            Packet response = second.read();
            assertNotNull(response);
            assertEquals(MqttCodec.PINGRESP, response.type());
        }
    }

    @Test
    void gracefulDisconnectClosesConnection() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("bye", 60)));
            client.send(new Packet(MqttCodec.DISCONNECT, 0, new byte[0]));
            client.assertClosed();
        }
    }

    @Test
    void silentClientIsDroppedAfterOneAndAHalfKeepAliveIntervals() throws Exception {
        try (var client = new RawClient(running.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("quiet", 1)));
            client.assertClosed();
        }
    }
}
