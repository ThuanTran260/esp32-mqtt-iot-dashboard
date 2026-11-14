package vn.edu.sgu.mqtt.protocol;

import java.io.IOException;

/** Thrown when bytes on the wire violate the MQTT framing or protocol rules. */
public class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
