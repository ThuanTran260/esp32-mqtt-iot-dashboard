package vn.edu.sgu.mqtt.protocol;

import java.util.Objects;

/**
 * One MQTT 3.1.1 control packet: fixed-header type (high nibble), flags (low nibble)
 * and the bytes following the Remaining Length field.
 */
public record Packet(int type, int flags, byte[] body) {
    public Packet {
        Objects.requireNonNull(body, "body");
    }
}
