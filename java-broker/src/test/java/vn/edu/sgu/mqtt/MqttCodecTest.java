package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.mqtt.protocol.FrameCodec;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscription;
import vn.edu.sgu.mqtt.protocol.Packet;
import vn.edu.sgu.mqtt.protocol.ProtocolException;

class MqttCodecTest {

    private static final int CONNECT = 1;
    private static final int PUBLISH = 3;
    private static final int PUBACK = 4;
    private static final int SUBSCRIBE = 8;
    private static final int UNSUBSCRIBE = 10;
    private static final int PINGREQ = 12;
    private static final int DISCONNECT = 14;

    // ---------- helpers ----------

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s);
    }

    private static byte[] cat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static byte[] u16(int v) {
        return new byte[] {(byte) (v >> 8), (byte) v};
    }

    private static byte[] str(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return cat(u16(b.length), b);
    }

    private static byte[] bin(byte[] b) {
        return cat(u16(b.length), b);
    }

    private static byte[] b(int... values) {
        byte[] r = new byte[values.length];
        for (int i = 0; i < values.length; i++) r[i] = (byte) values[i];
        return r;
    }

    /** CONNECT variable header with the given protocol name/level/flags/keepalive, then payload. */
    private static Packet connect(String proto, int level, int flags, int keepAlive,
                                  byte[]... payload) {
        return new Packet(CONNECT, 0,
            cat(str(proto), b(level, flags), u16(keepAlive), cat(payload)));
    }

    private static Packet validConnect(byte[]... payload) {
        return connect("MQTT", 4, 0x02, 30, payload);
    }

    private static String hexOf(Packet p) throws Exception {
        var out = new ByteArrayOutputStream();
        FrameCodec.write(out, p);
        return HexFormat.of().formatHex(out.toByteArray());
    }

    // ---------- CONNECT ----------

    @Test
    void decodesGoldenConnect() throws Exception {
        // MQTT level4, clean session, keepalive30, ClientId=abc.
        var packet = FrameCodec.read(new ByteArrayInputStream(
            HexFormat.of().parseHex("100f00044d5154540402001e0003616263")), 65536);
        var c = MqttCodec.decodeConnect(packet);
        assertEquals("abc", c.clientId());
        assertTrue(c.cleanSession());
        assertEquals(30, c.keepAliveSeconds());
        assertNull(c.username());
        assertNull(c.password());
        assertNull(c.will());
    }

    @Test
    void decodesConnectWithCredentialsAndWill() throws Exception {
        // username + password + will retain + will qos1 + will flag + clean session
        int flags = 0x80 | 0x40 | 0x20 | 0x08 | 0x04 | 0x02;
        byte[] pwd = b(0xff, 0xfe, 0x00, 0x80);
        byte[] willPayload = b(0x00, 0xc3);
        var c = MqttCodec.decodeConnect(connect("MQTT", 4, flags, 65535,
            str("dev-1"), str("sensor/status"), bin(willPayload), str("user1"), bin(pwd)));
        assertEquals("dev-1", c.clientId());
        assertEquals("user1", c.username());
        assertArrayEquals(pwd, c.password());
        assertTrue(c.cleanSession());
        assertEquals(65535, c.keepAliveSeconds());
        assertNotNull(c.will());
        assertEquals("sensor/status", c.will().topic());
        assertArrayEquals(willPayload, c.will().payload());
        assertEquals(1, c.will().qos());
        assertTrue(c.will().retain());
    }

    @Test
    void decodesConnectWithUsernameOnlyAndPersistentSession() throws Exception {
        var c = MqttCodec.decodeConnect(connect("MQTT", 4, 0x80, 0,
            str("dev-1"), str("user1")));
        assertEquals("user1", c.username());
        assertNull(c.password());
        assertFalse(c.cleanSession());
        assertEquals(0, c.keepAliveSeconds());
    }

    @Test
    void emptyClientIdAllowedOnlyWithCleanSession() throws Exception {
        var ok = MqttCodec.decodeConnect(validConnect(str("")));
        assertEquals("", ok.clientId());
        var ex = assertThrows(ProtocolException.class,
            () -> MqttCodec.decodeConnect(connect("MQTT", 4, 0x00, 30, str(""))));
        assertEquals(0x02, ex.connackCode());
    }

    @Test
    void unsupportedProtocolLevelCarriesConnackCode() {
        var ex = assertThrows(ProtocolException.class,
            () -> MqttCodec.decodeConnect(connect("MQTT", 3, 0x02, 30, str("abc"))));
        assertEquals(0x01, ex.connackCode());
        // A different protocol name is not a level problem: close without CONNACK.
        var bad = assertThrows(ProtocolException.class,
            () -> MqttCodec.decodeConnect(connect("MQIsdp", 3, 0x02, 30, str("abc"))));
        assertEquals(-1, bad.connackCode());
    }

    @Test
    void rejectsMalformedConnect() {
        // wrong protocol name
        assertCloses(connect("MQTX", 4, 0x02, 30, str("abc")));
        // reserved flag bit 0 set
        assertCloses(connect("MQTT", 4, 0x03, 30, str("abc")));
        // password without username
        assertCloses(connect("MQTT", 4, 0x40 | 0x02, 30, str("abc"), bin(b(1))));
        // will qos set but will flag clear
        assertCloses(connect("MQTT", 4, 0x08 | 0x02, 30, str("abc")));
        // will retain set but will flag clear
        assertCloses(connect("MQTT", 4, 0x20 | 0x02, 30, str("abc")));
        // will qos 3
        assertCloses(connect("MQTT", 4, 0x18 | 0x04 | 0x02, 30, str("abc"), str("t"), bin(b())));
        // trailing bytes
        assertCloses(validConnect(str("abc"), b(0x00)));
        // missing client id
        assertCloses(validConnect());
        // wrong fixed flags
        assertCloses(new Packet(CONNECT, 1, validConnect(str("abc")).body()));
        // wrong packet type
        assertCloses(new Packet(PUBLISH, 0, validConnect(str("abc")).body()));
        // will topic with wildcard
        assertCloses(connect("MQTT", 4, 0x04 | 0x02, 30, str("abc"), str("a/#"), bin(b())));
        // empty will topic
        assertCloses(connect("MQTT", 4, 0x04 | 0x02, 30, str("abc"), str(""), bin(b())));
    }

    @Test
    void rejectsTruncatedConnectFields() {
        // empty body, name length only, no level
        assertCloses(new Packet(CONNECT, 0, b()));
        assertCloses(new Packet(CONNECT, 0, b(0x00)));
        assertCloses(new Packet(CONNECT, 0, cat(str("MQTT"))));
        assertCloses(new Packet(CONNECT, 0, cat(str("MQTT"), b(4, 2))));
        assertCloses(new Packet(CONNECT, 0, cat(str("MQTT"), b(4, 2, 0))));
        // client id length claims 5 but only 3 follow
        assertCloses(validConnect(b(0x00, 0x05, 'a', 'b', 'c')));
        // truncated password
        assertCloses(connect("MQTT", 4, 0xC2, 30, str("abc"), str("u"), b(0x00, 0x04, 1, 2)));
        // missing password field entirely
        assertCloses(connect("MQTT", 4, 0xC2, 30, str("abc"), str("u")));
        // truncated will payload
        assertCloses(connect("MQTT", 4, 0x06, 30, str("abc"), str("t"), b(0x00, 0x03, 1)));
        // missing will payload length
        assertCloses(connect("MQTT", 4, 0x06, 30, str("abc"), str("t")));
        // password length prefix itself truncated
        assertCloses(connect("MQTT", 4, 0xC2, 30, str("abc"), str("u"), b(0x00)));
    }

    @Test
    void rejectsInvalidUtf8AndNulInStrings() {
        // 0xC3 0x28 is malformed UTF-8
        assertCloses(validConnect(b(0x00, 0x02, 0xc3, 0x28)));
        // overlong encoding of '/'
        assertCloses(validConnect(b(0x00, 0x02, 0xc0, 0xaf)));
        // lone UTF-16 surrogate encoded as UTF-8 (CESU style) is invalid
        assertCloses(validConnect(b(0x00, 0x03, 0xed, 0xa0, 0x80)));
        // NUL inside client id
        assertCloses(validConnect(b(0x00, 0x03, 'a', 0x00, 'b')));
        // malformed username
        assertCloses(connect("MQTT", 4, 0x82, 30, str("abc"), b(0x00, 0x01, 0xff)));
    }

    @Test
    void connectPasswordIsNotDecodedAsText() throws Exception {
        byte[] pwd = b(0xc3, 0x28, 0xff);
        var c = MqttCodec.decodeConnect(
            connect("MQTT", 4, 0xC2, 30, str("abc"), str("u"), bin(pwd)));
        assertArrayEquals(pwd, c.password());
    }

    private static void assertCloses(Packet p) {
        var ex = assertThrows(ProtocolException.class, () -> MqttCodec.decodeConnect(p));
        assertEquals(-1, ex.connackCode());
    }

    // ---------- ProtocolException ----------

    @Test
    void protocolExceptionConnackCodes() {
        assertEquals(-1, new ProtocolException("x").connackCode());
        var ex = new ProtocolException("level", 1);
        assertEquals(1, ex.connackCode());
        assertEquals("level", ex.getMessage());
    }

    // ---------- PUBLISH ----------

    @Test
    void decodesQos0Publish() throws Exception {
        // PUBLISH qos0, no retain: topic sensor/data payload {"t":1}
        byte[] payload = "{\"t\":1}".getBytes(StandardCharsets.UTF_8);
        var m = MqttCodec.decodePublish(new Packet(PUBLISH, 0,
            cat(str("sensor/data"), payload)));
        assertEquals("sensor/data", m.topic());
        assertArrayEquals(payload, m.payload());
        assertEquals(0, m.qos());
        assertFalse(m.retain());
        assertFalse(m.dup());
        assertEquals(0, m.packetId());
    }

    @Test
    void decodesQos1RetainedDupPublish() throws Exception {
        // flags: dup(8) | qos1(2) | retain(1)
        var m = MqttCodec.decodePublish(new Packet(PUBLISH, 0x0b,
            cat(str("sensor/led"), u16(0x1234), b(0xff, 0x00))));
        assertEquals("sensor/led", m.topic());
        assertArrayEquals(b(0xff, 0x00), m.payload());
        assertEquals(1, m.qos());
        assertTrue(m.retain());
        assertTrue(m.dup());
        assertEquals(0x1234, m.packetId());
    }

    @Test
    void decodesQos2PublishAsRequestedQos() throws Exception {
        var m = MqttCodec.decodePublish(new Packet(PUBLISH, 0x04,
            cat(str("a"), u16(1))));
        assertEquals(2, m.qos());
        assertEquals(0, m.payload().length);
    }

    @Test
    void decodesEmptyPayloadRetainClear() throws Exception {
        var m = MqttCodec.decodePublish(new Packet(PUBLISH, 0x01, cat(str("sensor/led"))));
        assertTrue(m.retain());
        assertEquals(0, m.payload().length);
    }

    @Test
    void rejectsMalformedPublish() {
        // QoS 3
        assertPublishRejected(new Packet(PUBLISH, 0x06, cat(str("a"), u16(1))));
        // QoS0 + DUP
        assertPublishRejected(new Packet(PUBLISH, 0x08, cat(str("a"))));
        // QoS1 packet id 0
        assertPublishRejected(new Packet(PUBLISH, 0x02, cat(str("a"), u16(0))));
        // QoS1 missing packet id
        assertPublishRejected(new Packet(PUBLISH, 0x02, cat(str("a"))));
        // QoS1 truncated packet id
        assertPublishRejected(new Packet(PUBLISH, 0x02, cat(str("a"), b(0x00))));
        // empty topic
        assertPublishRejected(new Packet(PUBLISH, 0, cat(str(""))));
        // wildcard topic
        assertPublishRejected(new Packet(PUBLISH, 0, cat(str("a/+"))));
        assertPublishRejected(new Packet(PUBLISH, 0, cat(str("a/#"))));
        // truncated topic
        assertPublishRejected(new Packet(PUBLISH, 0, b(0x00, 0x05, 'a')));
        // empty body
        assertPublishRejected(new Packet(PUBLISH, 0, b()));
        // invalid UTF-8 topic
        assertPublishRejected(new Packet(PUBLISH, 0, b(0x00, 0x02, 0xc3, 0x28)));
        // wrong packet type
        assertPublishRejected(new Packet(PUBACK, 0, cat(str("a"))));
    }

    private static void assertPublishRejected(Packet p) {
        assertThrows(ProtocolException.class, () -> MqttCodec.decodePublish(p));
    }

    // ---------- SUBSCRIBE / UNSUBSCRIBE ----------

    @Test
    void decodesGoldenSubscribe() throws Exception {
        var packet = FrameCodec.read(new ByteArrayInputStream(hex("8208000a0003612f6201")), 65536);
        var s = MqttCodec.decodeSubscribe(packet);
        assertEquals(10, s.packetId());
        assertEquals(List.of(new Subscription("a/b", 1)), s.filters());
    }

    @Test
    void decodesMultiFilterSubscribeWithRequestedQos() throws Exception {
        var s = MqttCodec.decodeSubscribe(new Packet(SUBSCRIBE, 2, cat(
            u16(7), str("sensor/data"), b(0), str("sensor/+"), b(1), str("#"), b(2))));
        assertEquals(7, s.packetId());
        assertEquals(List.of(
            new Subscription("sensor/data", 0),
            new Subscription("sensor/+", 1),
            new Subscription("#", 2)), s.filters());
    }

    @Test
    void rejectsMalformedSubscribe() {
        // wrong fixed flags
        assertSubscribeRejected(new Packet(SUBSCRIBE, 0, cat(u16(1), str("a"), b(0))));
        assertSubscribeRejected(new Packet(SUBSCRIBE, 3, cat(u16(1), str("a"), b(0))));
        // packet id 0
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(0), str("a"), b(0))));
        // no filters
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1))));
        // truncated packet id
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, b(0x00)));
        // missing qos byte
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str("a"))));
        // requested qos 3
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str("a"), b(3))));
        // reserved bits in options byte
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str("a"), b(0x04))));
        // invalid filter
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str("a/#/b"), b(0))));
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str(""), b(0))));
        // second filter malformed after a valid one
        assertSubscribeRejected(new Packet(SUBSCRIBE, 2,
            cat(u16(1), str("a"), b(0), b(0x00, 0x09, 'x'))));
        // wrong type
        assertSubscribeRejected(new Packet(UNSUBSCRIBE, 2, cat(u16(1), str("a"), b(0))));
    }

    private static void assertSubscribeRejected(Packet p) {
        assertThrows(ProtocolException.class, () -> MqttCodec.decodeSubscribe(p));
    }

    @Test
    void decodesUnsubscribe() throws Exception {
        var u = MqttCodec.decodeUnsubscribe(new Packet(UNSUBSCRIBE, 2,
            cat(u16(9), str("sensor/data"), str("sensor/+"))));
        assertEquals(9, u.packetId());
        assertEquals(List.of("sensor/data", "sensor/+"), u.filters());
    }

    @Test
    void rejectsMalformedUnsubscribe() {
        assertUnsubscribeRejected(new Packet(UNSUBSCRIBE, 0, cat(u16(1), str("a"))));
        assertUnsubscribeRejected(new Packet(UNSUBSCRIBE, 2, cat(u16(0), str("a"))));
        assertUnsubscribeRejected(new Packet(UNSUBSCRIBE, 2, cat(u16(1))));
        assertUnsubscribeRejected(new Packet(UNSUBSCRIBE, 2, cat(u16(1), str("a#"))));
        assertUnsubscribeRejected(new Packet(UNSUBSCRIBE, 2, cat(u16(1), b(0x00, 0x04, 'a'))));
        assertUnsubscribeRejected(new Packet(SUBSCRIBE, 2, cat(u16(1), str("a"))));
    }

    private static void assertUnsubscribeRejected(Packet p) {
        assertThrows(ProtocolException.class, () -> MqttCodec.decodeUnsubscribe(p));
    }

    // ---------- packetId / PINGREQ / DISCONNECT ----------

    @Test
    void readsPubackPacketId() throws Exception {
        assertEquals(7, MqttCodec.packetId(new Packet(PUBACK, 0, u16(7))));
        assertEquals(65535, MqttCodec.packetId(new Packet(PUBACK, 0, u16(65535))));
    }

    @Test
    void rejectsBadPacketId() {
        assertThrows(ProtocolException.class,
            () -> MqttCodec.packetId(new Packet(PUBACK, 0, u16(0))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.packetId(new Packet(PUBACK, 0, b(0x00))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.packetId(new Packet(PUBACK, 0, b(0x00, 0x01, 0x00))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.packetId(new Packet(PUBACK, 1, u16(1))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.packetId(new Packet(PINGREQ, 0, u16(1))));
    }

    @Test
    void validatesPingreqAndDisconnect() throws Exception {
        MqttCodec.checkPingreq(new Packet(PINGREQ, 0, b()));
        MqttCodec.checkDisconnect(new Packet(DISCONNECT, 0, b()));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.checkPingreq(new Packet(PINGREQ, 1, b())));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.checkPingreq(new Packet(PINGREQ, 0, b(0))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.checkDisconnect(new Packet(DISCONNECT, 2, b())));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.checkDisconnect(new Packet(DISCONNECT, 0, b(0))));
        assertThrows(ProtocolException.class,
            () -> MqttCodec.checkDisconnect(new Packet(PINGREQ, 0, b())));
    }

    // ---------- encoders ----------

    @Test
    void encodesGoldenResponses() throws Exception {
        assertEquals("20020000", hexOf(MqttCodec.connack(0)));
        assertEquals("20020005", hexOf(MqttCodec.connack(5)));
        assertEquals("40020007", hexOf(MqttCodec.puback(7)));
        assertEquals("900400070001", hexOf(MqttCodec.suback(7, List.of(0, 1))));
        assertEquals("9003000780", hexOf(MqttCodec.suback(7, List.of(0x80))));
        assertEquals("b0020007", hexOf(MqttCodec.unsuback(7)));
        assertEquals("d000", hexOf(MqttCodec.pingresp()));
    }

    @Test
    void rejectsInvalidResponseArguments() {
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.connack(6));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.connack(-1));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.puback(0));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.puback(65536));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.unsuback(0));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.suback(0, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.suback(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.suback(1, List.of(3)));
        assertThrows(IllegalArgumentException.class, () -> MqttCodec.suback(1, List.of(-1)));
    }

    @Test
    void encodesQos0Publish() throws Exception {
        var p = MqttCodec.publish(new Publish("a/b", b(0x68, 0x69), 0, false, false, 0));
        assertEquals("30070003612f626869", hexOf(p));
    }

    @Test
    void encodesQos1RetainedPublish() throws Exception {
        var p = MqttCodec.publish(new Publish("a/b", b(0x68, 0x69), 1, true, false, 0x0102));
        assertEquals("33090003612f6201026869", hexOf(p));
    }

    @Test
    void publishEncodeDecodeRoundTrip() throws Exception {
        var original = new Publish("sensor/led", b(0x00, 0xff, 0x80), 1, true, true, 513);
        var decoded = MqttCodec.decodePublish(MqttCodec.publish(original));
        assertEquals(original.topic(), decoded.topic());
        assertArrayEquals(original.payload(), decoded.payload());
        assertEquals(original.qos(), decoded.qos());
        assertEquals(original.retain(), decoded.retain());
        assertEquals(original.dup(), decoded.dup());
        assertEquals(original.packetId(), decoded.packetId());
    }

    @Test
    void rejectsInvalidPublishToEncode() {
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("a", b(), 3, false, false, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("a", b(), 1, false, false, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("a", b(), 0, false, true, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("", b(), 0, false, false, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("a/#", b(), 0, false, false, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> MqttCodec.publish(new Publish("a", null, 0, false, false, 0)));
    }
}
