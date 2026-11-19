package vn.edu.sgu.mqtt.protocol;

import java.util.List;

/** Decoded MQTT 3.1.1 control packet contents. */
public final class MqttMessages {
    private MqttMessages() {
    }

    public record Will(String topic, byte[] payload, int qos, boolean retain) {}

    public record Connect(String clientId, String username, byte[] password,
                          boolean cleanSession, int keepAliveSeconds, Will will) {}

    public record Publish(String topic, byte[] payload, int qos, boolean retain,
                          boolean dup, int packetId) {}

    public record Subscription(String filter, int qos) {}

    public record Subscribe(int packetId, List<Subscription> filters) {}

    public record Unsubscribe(int packetId, List<String> filters) {}
}
