package vn.edu.sgu.mqtt.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Bounded MQTT 3.1.1 fixed-header framing over blocking streams. */
public final class FrameCodec {
    /** Largest body {@link #write} accepts. */
    public static final int MAX_WRITE_BODY = 65536;

    private FrameCodec() {
    }

    /**
     * Reads one packet.
     *
     * @return the packet, or {@code null} on EOF before the first byte
     * @throws EOFException if the stream ends inside a packet
     * @throws ProtocolException if the remaining length exceeds {@code maxBytes} or four bytes
     */
    public static Packet read(InputStream in, int maxBytes) throws IOException {
        int first = in.read();
        if (first == -1) return null;
        int length = 0;
        int multiplier = 1;
        for (int i = 0; i < 4; i++) {
            int digit = in.read();
            if (digit == -1) throw new EOFException("Truncated remaining length");
            length += (digit & 127) * multiplier;
            if (length > maxBytes) throw new ProtocolException("Packet too large");
            if ((digit & 128) == 0) {
                byte[] body = in.readNBytes(length);
                if (body.length != length) throw new EOFException("Truncated body");
                return new Packet(first >>> 4, first & 15, body);
            }
            multiplier *= 128;
        }
        throw new ProtocolException("Remaining length exceeds four bytes");
    }

    /**
     * Writes one packet. Does not flush or close the stream; the session writer flushes.
     *
     * @throws IllegalArgumentException if type is not 1..14, flags not 0..15,
     *     or the body is longer than {@value #MAX_WRITE_BODY} bytes
     */
    public static void write(OutputStream out, Packet packet) throws IOException {
        if (packet.type() < 1 || packet.type() > 14) {
            throw new IllegalArgumentException("Invalid packet type: " + packet.type());
        }
        if (packet.flags() < 0 || packet.flags() > 15) {
            throw new IllegalArgumentException("Invalid packet flags: " + packet.flags());
        }
        if (packet.body().length > MAX_WRITE_BODY) {
            throw new IllegalArgumentException("Packet body too large: " + packet.body().length);
        }
        int remaining = packet.body().length;
        out.write((packet.type() << 4) | packet.flags());
        do {
            int digit = remaining % 128;
            remaining /= 128;
            out.write(digit | (remaining == 0 ? 0 : 128));
        } while (remaining != 0);
        out.write(packet.body());
    }
}
