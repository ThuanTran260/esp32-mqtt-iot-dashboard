package vn.edu.sgu.iot.mqtt;

import java.util.concurrent.CompletionStage;

/**
 * UI-agnostic MQTT client. Every method returns quickly; callbacks arrive on transport
 * threads, so a Swing UI must hop to the EDT itself.
 */
public interface MqttTransport extends AutoCloseable {

    interface Listener {
        void onConnection(ConnectionState state);

        void onMessage(String topic, byte[] payload);

        /** A short, human-readable reason; never contains the password. */
        void onError(String message);
    }

    /** Completes once connected and subscribed; fails (never hangs) otherwise. */
    CompletionStage<Void> connect(ConnectionSettings settings, Listener listener);

    CompletionStage<Void> disconnect();

    /** Completes when the client has handed the message over; fails when offline. */
    CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain);

    boolean isConnected();

    /** Idempotent; releases all resources without blocking the caller. */
    @Override
    void close();
}
