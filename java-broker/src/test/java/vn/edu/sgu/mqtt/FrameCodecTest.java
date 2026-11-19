package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.mqtt.protocol.FrameCodec;
import vn.edu.sgu.mqtt.protocol.Packet;
import vn.edu.sgu.mqtt.protocol.ProtocolException;

class FrameCodecTest {

    /** InputStream that returns at most one byte per read call, to simulate TCP chunking. */
    private static final class OneByteInputStream extends InputStream {
        private final InputStream delegate;

        OneByteInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() {
            try {
                return delegate.read();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) return 0;
            int value = read();
            if (value == -1) return -1;
            b[off] = (byte) value;
            return 1;
        }
    }

    @Test
    void readsCoalescedPacketsAndCleanEof() throws Exception {
        var input = new ByteArrayInputStream(HexFormat.of().parseHex("c000e000"));
        assertEquals(12, FrameCodec.read(input, 65536).type());
        assertEquals(14, FrameCodec.read(input, 65536).type());
        assertNull(FrameCodec.read(input, 65536));
    }

    @Test
    void rejectsTruncatedAndOversizedLength() {
        assertThrows(EOFException.class, () -> FrameCodec.read(
            new ByteArrayInputStream(HexFormat.of().parseHex("300500")), 65536));
        assertThrows(ProtocolException.class, () -> FrameCodec.read(
            new ByteArrayInputStream(HexFormat.of().parseHex("30818004")), 65536));
    }

    @Test
    void readsTwoByteRemainingLengthFromOneByteChunks() throws Exception {
        byte[] body = new byte[128];
        for (int i = 0; i < body.length; i++) body[i] = (byte) i;
        byte[] frame = new byte[3 + body.length];
        frame[0] = 0x32; // PUBLISH, QoS 1
        frame[1] = (byte) 0x80;
        frame[2] = 0x01;
        System.arraycopy(body, 0, frame, 3, body.length);

        var input = new OneByteInputStream(frame);
        Packet packet = FrameCodec.read(input, 65536);
        assertEquals(3, packet.type());
        assertEquals(2, packet.flags());
        assertArrayEquals(body, packet.body());
        assertNull(FrameCodec.read(input, 65536));
    }

    @Test
    void rejectsEofInsideRemainingLength() {
        assertThrows(EOFException.class, () -> FrameCodec.read(
            new OneByteInputStream(HexFormat.of().parseHex("3080")), 65536));
    }

    @Test
    void rejectsContinuationBitOnFourthLengthByte() {
        assertThrows(ProtocolException.class, () -> FrameCodec.read(
            new ByteArrayInputStream(HexFormat.of().parseHex("3080808080")), Integer.MAX_VALUE));
    }

    @Test
    void writesPingrespGoldenBytes() throws Exception {
        var out = new ByteArrayOutputStream();
        FrameCodec.write(out, new Packet(13, 0, new byte[0]));
        assertEquals("d000", HexFormat.of().formatHex(out.toByteArray()));
    }

    @Test
    void writesTwoByteRemainingLength() throws Exception {
        byte[] body = new byte[128];
        Arrays.fill(body, (byte) 0x5a);
        var out = new ByteArrayOutputStream();
        FrameCodec.write(out, new Packet(3, 0, body));
        byte[] bytes = out.toByteArray();
        assertEquals(131, bytes.length);
        assertEquals("308001", HexFormat.of().formatHex(bytes, 0, 3));
        assertArrayEquals(body, Arrays.copyOfRange(bytes, 3, bytes.length));
    }

    @Test
    void rejectsInvalidPacketsOnWrite() {
        var out = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class,
            () -> FrameCodec.write(out, new Packet(0, 0, new byte[0])));
        assertThrows(IllegalArgumentException.class,
            () -> FrameCodec.write(out, new Packet(15, 0, new byte[0])));
        assertThrows(IllegalArgumentException.class,
            () -> FrameCodec.write(out, new Packet(3, 16, new byte[0])));
        assertThrows(IllegalArgumentException.class,
            () -> FrameCodec.write(out, new Packet(3, -1, new byte[0])));
        assertThrows(IllegalArgumentException.class,
            () -> FrameCodec.write(out, new Packet(3, 0, new byte[65537])));
        assertEquals(0, out.size());
    }
}
