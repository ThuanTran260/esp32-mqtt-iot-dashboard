package vn.edu.sgu.mqtt.protocol;

import java.io.IOException;

/** Thrown when bytes on the wire violate the MQTT framing or protocol rules. */
public class ProtocolException extends IOException {
    private final int connackCode;

    /** Violation that requires closing the connection immediately (no CONNACK). */
    public ProtocolException(String message) {
        this(message, -1);
    }

    /**
     * @param connackCode CONNACK return code the session should send before closing,
     *     or -1 to close without a CONNACK
     */
    public ProtocolException(String message, int connackCode) {
        super(message);
        this.connackCode = connackCode;
    }

    /** CONNACK refusal code to send before closing, or -1 if the connection just closes. */
    public int connackCode() {
        return connackCode;
    }
}
