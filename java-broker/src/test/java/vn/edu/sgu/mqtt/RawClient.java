package vn.edu.sgu.mqtt;

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
