package vn.edu.sgu.iot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;

/** In-memory transport for CLI tests; no sockets, no threads. */
class FakeTransport implements MqttTransport {
    volatile boolean connected;
    volatile Listener listener;
    final List<String> sent = new ArrayList<>();
    /** When set, connect fails; {@link #errorMessage} (if non-null) is reported to the listener first. */
    volatile boolean failConnect;
    volatile String errorMessage;
    volatile int disconnectCalls;
    volatile boolean closed;

    @Override
    public CompletionStage<Void> connect(ConnectionSettings settings, Listener listener) {
        this.listener = listener;
        if (failConnect) {
            if (errorMessage != null) {
                listener.onError(errorMessage);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("connect failed"));
        }
        connected = true;
        listener.onConnection(ConnectionState.CONNECTED);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disconnect() {
        disconnectCalls++;
        connected = false;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
        if (!connected) {
            return CompletableFuture.failedFuture(new IllegalStateException("offline"));
        }
        synchronized (sent) {
            sent.add(topic + ":" + new String(payload, StandardCharsets.UTF_8));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        closed = true;
        connected = false;
    }
}
