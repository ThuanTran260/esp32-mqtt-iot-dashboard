package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import vn.edu.sgu.mqtt.protocol.FrameCodec;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.Packet;

/** Test helper: a raw TCP client that speaks MQTT bytes directly, with 3 s read timeouts. */
public final class RawClient implements AutoCloseable {
    /** CONNECT flags: clean session, username and password present. */
    public static final int CLEAN_WITH_CREDENTIALS = 0xC2;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public RawClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(3000);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
    }

    public static Packet connect(int level, int flags, int keepAlive, String clientId,
                                 String user, String password) {
        try {
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeUTF("MQTT");
            data.writeByte(level);
            data.writeByte(flags);
            data.writeShort(keepAlive);
            data.writeUTF(clientId);
            if ((flags & 0x80) != 0) data.writeUTF(user);
            if ((flags & 0x40) != 0) data.writeUTF(password);
            return new Packet(MqttCodec.CONNECT, 0, bytes.toByteArray());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    public static Packet validConnect(String clientId, int keepAlive) {
        return connect(4, CLEAN_WITH_CREDENTIALS, keepAlive, clientId,
            RunningBroker.USER, RunningBroker.PASSWORD);
    }


    /** CONNECT with credentials, clean session and a Will message. */
    public static Packet connectWithWill(String clientId, int keepAlive, String willTopic,
                                         byte[] willPayload, int willQos, boolean willRetain) {
        try {
            int flags = CLEAN_WITH_CREDENTIALS | 0x04 | (willQos << 3) | (willRetain ? 0x20 : 0);
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeUTF("MQTT");
            data.writeByte(4);
            data.writeByte(flags);
            data.writeShort(keepAlive);
            data.writeUTF(clientId);
            data.writeUTF(willTopic);
            data.writeShort(willPayload.length);
            data.write(willPayload);
            data.writeUTF(RunningBroker.USER);
            data.writeUTF(RunningBroker.PASSWORD);
            return new Packet(MqttCodec.CONNECT, 0, bytes.toByteArray());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    public static Packet subscribePacket(int packetId, int requestedQos, String... filters) {
        try {
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeShort(packetId);
            for (String filter : filters) {
                data.writeUTF(filter);
                data.writeByte(requestedQos);
            }
            return new Packet(MqttCodec.SUBSCRIBE, 2, bytes.toByteArray());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    public static Packet unsubscribePacket(int packetId, String... filters) {
        try {
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeShort(packetId);
            for (String filter : filters) data.writeUTF(filter);
            return new Packet(MqttCodec.UNSUBSCRIBE, 2, bytes.toByteArray());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    /** Connects (code 0 expected) and returns the ready client. */
    public static RawClient connected(int port, String clientId, int keepAlive) throws IOException {
        var client = new RawClient(port);
        try {
            int code = client.connectAndGetCode(validConnect(clientId, keepAlive));
            if (code != 0) fail("CONNACK code " + code);
            return client;
        } catch (IOException | RuntimeException | Error ex) {
            client.close();
            throw ex;
        }
    }

    /** Subscribes at requested QoS 0 and returns the SUBACK grants. */
    public byte[] subscribe(int packetId, String... filters) throws IOException {
        return subscribe(packetId, 0, filters);
    }

    public byte[] subscribe(int packetId, int requestedQos, String... filters) throws IOException {
        send(subscribePacket(packetId, requestedQos, filters));
        Packet ack = read();
        if (ack == null || ack.type() != MqttCodec.SUBACK) fail("Expected SUBACK, got " + ack);
        return java.util.Arrays.copyOfRange(ack.body(), 2, ack.body().length);
    }

    /** Publishes at QoS 1 and waits for the PUBACK. */
    public void publishQos1(String topic, byte[] payload, boolean retain, int packetId)
            throws IOException {
        send(MqttCodec.publish(new vn.edu.sgu.mqtt.protocol.MqttMessages.Publish(
            topic, payload, 1, retain, false, packetId)));
        Packet ack = read();
        if (ack == null || ack.type() != MqttCodec.PUBACK) fail("Expected PUBACK, got " + ack);
        assertEquals(packetId, MqttCodec.packetId(ack));
    }

    /** Publishes at QoS 0 (no acknowledgement expected). */
    public void publishQos0(String topic, byte[] payload, boolean retain) throws IOException {
        send(MqttCodec.publish(new vn.edu.sgu.mqtt.protocol.MqttMessages.Publish(
            topic, payload, 0, retain, false, 0)));
    }

    /** Unsubscribes and waits for the UNSUBACK carrying the same packet ID. */
    public void unsubscribe(int packetId, String... filters) throws IOException {
        send(unsubscribePacket(packetId, filters));
        Packet ack = read();
        if (ack == null || ack.type() != MqttCodec.UNSUBACK) fail("Expected UNSUBACK, got " + ack);
        assertEquals(packetId, ((ack.body()[0] & 0xFF) << 8) | (ack.body()[1] & 0xFF));
    }

    /** Reads the next packet, which must be a PUBLISH. */
    public vn.edu.sgu.mqtt.protocol.MqttMessages.Publish readPublish() throws IOException {
        Packet packet = read();
        if (packet == null || packet.type() != MqttCodec.PUBLISH) fail("Expected PUBLISH, got " + packet);
        return MqttCodec.decodePublish(packet);
    }

    /** PINGREQ/PINGRESP round trip: proves nothing else was queued before the PINGRESP. */
    public void assertNothingPendingBeforePing() throws IOException {
        send(new Packet(MqttCodec.PINGREQ, 0, new byte[0]));
        Packet packet = read();
        if (packet == null || packet.type() != MqttCodec.PINGRESP) fail("Expected PINGRESP, got " + packet);
    }

    public void send(Packet packet) throws IOException {
        FrameCodec.write(out, packet);
        out.flush();
    }

    /** Reads one packet; null means the peer closed the connection cleanly. */
    public Packet read() throws IOException {
        return FrameCodec.read(in, 4096);
    }

    /** Sends a valid CONNECT and returns the CONNACK return code. */
    public int connectAndGetCode(Packet connect) throws IOException {
        send(connect);
        Packet ack = read();
        if (ack == null || ack.type() != MqttCodec.CONNACK) fail("Expected CONNACK, got " + ack);
        return ack.body()[1];
    }

    /** Fails unless the server closes the connection (EOF or reset) within the 3 s read timeout. */
    public void assertClosed() {
        try {
            assertNull(read(), "Expected EOF but received a packet");
        } catch (SocketTimeoutException ex) {
            fail("Connection was not closed within 3 s");
        } catch (IOException ex) {
            // reset or truncated stream also means closed
        }
    }

    public Socket socket() {
        return socket;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
