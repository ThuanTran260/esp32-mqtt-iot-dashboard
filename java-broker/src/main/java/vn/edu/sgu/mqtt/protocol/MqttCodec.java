package vn.edu.sgu.mqtt.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Connect;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscribe;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscription;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Unsubscribe;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Will;
import vn.edu.sgu.mqtt.routing.TopicFilter;

/**
 * Decodes and encodes MQTT 3.1.1 control packet bodies. Decoders validate fixed-header flags,
 * field structure, UTF-8 and packet IDs, and report violations as {@link ProtocolException}.
 * Policy (authentication, granted QoS, duplicate CONNECT) belongs to the session layer.
 */
public final class MqttCodec {
    public static final int CONNECT = 1;
    public static final int CONNACK = 2;
    public static final int PUBLISH = 3;
    public static final int PUBACK = 4;
    public static final int SUBSCRIBE = 8;
    public static final int SUBACK = 9;
    public static final int UNSUBSCRIBE = 10;
    public static final int UNSUBACK = 11;
    public static final int PINGREQ = 12;
    public static final int PINGRESP = 13;
    public static final int DISCONNECT = 14;

    /** CONNACK code for an unsupported protocol level. */
    public static final int CONNACK_UNACCEPTABLE_PROTOCOL = 0x01;
    /** CONNACK code for a rejected (empty, non-clean-session) client identifier. */
    public static final int CONNACK_IDENTIFIER_REJECTED = 0x02;

    private static final int MAX_STRING = 65535;
    private static final byte[] EMPTY = new byte[0];

    private MqttCodec() {
    }

    // ------------------------------------------------------------------ decoding

    /** Decodes CONNECT. Does not authenticate; level/id refusals carry a CONNACK code. */
    public static Connect decodeConnect(Packet packet) throws ProtocolException {
        requireHeader(packet, CONNECT, 0, "CONNECT");
        ByteBuffer buf = ByteBuffer.wrap(packet.body());
        if (!"MQTT".equals(text(buf))) {
            throw new ProtocolException("Unknown protocol name");
        }
        int level = u8(buf);
        if (level != 4) {
            throw new ProtocolException("Unsupported protocol level " + level,
                CONNACK_UNACCEPTABLE_PROTOCOL);
        }
        int flags = u8(buf);
        if ((flags & 0x01) != 0) {
            throw new ProtocolException("CONNECT reserved flag set");
        }
        boolean cleanSession = (flags & 0x02) != 0;
        boolean willFlag = (flags & 0x04) != 0;
        int willQos = (flags >> 3) & 3;
        boolean willRetain = (flags & 0x20) != 0;
        boolean passwordFlag = (flags & 0x40) != 0;
        boolean usernameFlag = (flags & 0x80) != 0;
        if (willFlag) {
            if (willQos == 3) throw new ProtocolException("Will QoS 3");
        } else if (willQos != 0 || willRetain) {
            throw new ProtocolException("Will QoS/retain set without Will flag");
        }
        if (passwordFlag && !usernameFlag) {
            throw new ProtocolException("Password flag without username flag");
        }
        int keepAlive = u16(buf);
        String clientId = text(buf);
        Will will = null;
        if (willFlag) {
            String willTopic = text(buf);
            TopicFilter.validateTopic(willTopic);
            will = new Will(willTopic, binary(buf), willQos, willRetain);
        }
        String username = usernameFlag ? text(buf) : null;
        byte[] password = passwordFlag ? binary(buf) : null;
        exhausted(buf);
        if (clientId.isEmpty() && !cleanSession) {
            throw new ProtocolException("Empty client id requires clean session",
                CONNACK_IDENTIFIER_REJECTED);
        }
        return new Connect(clientId, username, password, cleanSession, keepAlive, will);
    }

    /** Decodes PUBLISH; the requested QoS (0..2) is returned as sent. */
    public static Publish decodePublish(Packet packet) throws ProtocolException {
        if (packet.type() != PUBLISH) throw new ProtocolException("Expected PUBLISH");
        int flags = packet.flags();
        boolean dup = (flags & 0x08) != 0;
        int qos = (flags >> 1) & 3;
        boolean retain = (flags & 0x01) != 0;
        if (qos == 3) throw new ProtocolException("PUBLISH QoS 3");
        if (qos == 0 && dup) throw new ProtocolException("PUBLISH QoS 0 with DUP");
        ByteBuffer buf = ByteBuffer.wrap(packet.body());
        String topic = text(buf);
        TopicFilter.validateTopic(topic);
        int packetId = 0;
        if (qos > 0) packetId = packetIdField(buf);
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new Publish(topic, payload, qos, retain, dup, packetId);
    }

    /** Decodes SUBSCRIBE; each filter carries the QoS the client requested. */
    public static Subscribe decodeSubscribe(Packet packet) throws ProtocolException {
        requireHeader(packet, SUBSCRIBE, 2, "SUBSCRIBE");
        ByteBuffer buf = ByteBuffer.wrap(packet.body());
        int packetId = packetIdField(buf);
        if (!buf.hasRemaining()) throw new ProtocolException("SUBSCRIBE without filters");
        List<Subscription> filters = new ArrayList<>();
        while (buf.hasRemaining()) {
            String filter = text(buf);
            TopicFilter.validateFilter(filter);
            int options = u8(buf);
            if ((options & 0xFC) != 0 || (options & 3) == 3) {
                throw new ProtocolException("Invalid requested QoS byte");
            }
            filters.add(new Subscription(filter, options));
        }
        return new Subscribe(packetId, List.copyOf(filters));
    }

    public static Unsubscribe decodeUnsubscribe(Packet packet) throws ProtocolException {
        requireHeader(packet, UNSUBSCRIBE, 2, "UNSUBSCRIBE");
        ByteBuffer buf = ByteBuffer.wrap(packet.body());
        int packetId = packetIdField(buf);
        if (!buf.hasRemaining()) throw new ProtocolException("UNSUBSCRIBE without filters");
        List<String> filters = new ArrayList<>();
        while (buf.hasRemaining()) {
            String filter = text(buf);
            TopicFilter.validateFilter(filter);
            filters.add(filter);
        }
        return new Unsubscribe(packetId, List.copyOf(filters));
    }

    /** Reads the packet ID of a PUBACK (the only ID-only packet a QoS 0/1 broker receives). */
    public static int packetId(Packet packet) throws ProtocolException {
        requireHeader(packet, PUBACK, 0, "PUBACK");
        ByteBuffer buf = ByteBuffer.wrap(packet.body());
        int id = packetIdField(buf);
        exhausted(buf);
        return id;
    }

    /** Validates a PINGREQ: fixed flags 0 and an empty body. */
    public static void checkPingreq(Packet packet) throws ProtocolException {
        requireHeader(packet, PINGREQ, 0, "PINGREQ");
        if (packet.body().length != 0) throw new ProtocolException("PINGREQ has a body");
    }

    /** Validates a DISCONNECT: fixed flags 0 and an empty body. */
    public static void checkDisconnect(Packet packet) throws ProtocolException {
        requireHeader(packet, DISCONNECT, 0, "DISCONNECT");
        if (packet.body().length != 0) throw new ProtocolException("DISCONNECT has a body");
    }

    // ------------------------------------------------------------------ encoding

    public static Packet connack(int code) {
        if (code < 0 || code > 5) throw new IllegalArgumentException("Invalid CONNACK code: " + code);
        return new Packet(CONNACK, 0, new byte[] {0, (byte) code});
    }

    public static Packet suback(int id, List<Integer> grants) {
        checkId(id);
        if (grants.isEmpty()) throw new IllegalArgumentException("SUBACK needs at least one grant");
        byte[] body = new byte[2 + grants.size()];
        body[0] = (byte) (id >> 8);
        body[1] = (byte) id;
        for (int i = 0; i < grants.size(); i++) {
            int g = grants.get(i);
            if (g != 0 && g != 1 && g != 2 && g != 0x80) {
                throw new IllegalArgumentException("Invalid SUBACK grant: " + g);
            }
            body[2 + i] = (byte) g;
        }
        return new Packet(SUBACK, 0, body);
    }

    public static Packet puback(int id) {
        return idOnly(PUBACK, id);
    }

    public static Packet unsuback(int id) {
        return idOnly(UNSUBACK, id);
    }

    public static Packet pingresp() {
        return new Packet(PINGRESP, 0, EMPTY);
    }

    /** Encodes an outbound PUBLISH; rejects what {@link #decodePublish} would reject. */
    public static Packet publish(Publish m) {
        if (m.payload() == null) throw new IllegalArgumentException("PUBLISH payload is null");
        if (m.qos() < 0 || m.qos() > 2) throw new IllegalArgumentException("Invalid QoS: " + m.qos());
        if (m.qos() == 0 && m.dup()) throw new IllegalArgumentException("QoS 0 PUBLISH with DUP");
        try {
            TopicFilter.validateTopic(m.topic());
        } catch (ProtocolException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
        var out = new ByteArrayOutputStream(m.payload().length + m.topic().length() + 4);
        byte[] topic = m.topic().getBytes(StandardCharsets.UTF_8);
        out.write(topic.length >> 8);
        out.write(topic.length);
        out.writeBytes(topic);
        if (m.qos() > 0) {
            checkId(m.packetId());
            out.write(m.packetId() >> 8);
            out.write(m.packetId());
        }
        out.writeBytes(m.payload());
        int flags = (m.dup() ? 8 : 0) | (m.qos() << 1) | (m.retain() ? 1 : 0);
        return new Packet(PUBLISH, flags, out.toByteArray());
    }

    // ------------------------------------------------------------------ helpers

    private static Packet idOnly(int type, int id) {
        checkId(id);
        return new Packet(type, 0, new byte[] {(byte) (id >> 8), (byte) id});
    }

    private static void checkId(int id) {
        if (id < 1 || id > 65535) throw new IllegalArgumentException("Invalid packet id: " + id);
    }

    private static void requireHeader(Packet p, int type, int flags, String name)
            throws ProtocolException {
        if (p.type() != type) throw new ProtocolException("Expected " + name);
        if (p.flags() != flags) throw new ProtocolException("Invalid " + name + " flags");
    }

    private static int packetIdField(ByteBuffer buf) throws ProtocolException {
        int id = u16(buf);
        if (id == 0) throw new ProtocolException("Packet id 0");
        return id;
    }

    private static int u8(ByteBuffer buf) throws ProtocolException {
        try {
            return buf.get() & 0xFF;
        } catch (BufferUnderflowException ex) {
            throw new ProtocolException("Truncated packet");
        }
    }

    private static int u16(ByteBuffer buf) throws ProtocolException {
        try {
            return buf.getShort() & 0xFFFF;
        } catch (BufferUnderflowException ex) {
            throw new ProtocolException("Truncated packet");
        }
    }

    /** Length-prefixed raw bytes (passwords, payloads): never decoded as text. */
    private static byte[] binary(ByteBuffer buf) throws ProtocolException {
        int length = u16(buf);
        if (buf.remaining() < length) throw new ProtocolException("Truncated field");
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return bytes;
    }

    /** Length-prefixed strict UTF-8 without NUL. */
    private static String text(ByteBuffer buf) throws ProtocolException {
        byte[] bytes = binary(buf);
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        String value;
        try {
            value = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new ProtocolException("Invalid UTF-8");
        }
        if (value.indexOf('\0') >= 0) throw new ProtocolException("NUL in MQTT string");
        return value;
    }

    private static void exhausted(ByteBuffer buf) throws ProtocolException {
        if (buf.hasRemaining()) throw new ProtocolException("Trailing bytes in packet");
    }
}
