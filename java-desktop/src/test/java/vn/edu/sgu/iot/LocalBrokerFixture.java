package vn.edu.sgu.iot;

import java.io.IOException;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.mqtt.server.BrokerConfig;
import vn.edu.sgu.mqtt.server.BrokerServer;

/** Test fixture: the Java broker on a loopback ephemeral port with the demo account. */
public final class LocalBrokerFixture implements AutoCloseable {
    public static final String USER = "user1";
    public static final String PASSWORD = "123456";

    private BrokerServer server;
    private final int port;

    private LocalBrokerFixture(BrokerServer server) {
        this.server = server;
        this.port = server.port();
    }

    public static LocalBrokerFixture open() throws IOException {
        return new LocalBrokerFixture(startBroker(0));
    }

    private static BrokerServer startBroker(int port) throws IOException {
        var server = new BrokerServer(new BrokerConfig("127.0.0.1", port, USER, PASSWORD));
        server.start();
        return server;
    }

    public BrokerServer server() {
        return server;
    }

    public int port() {
        return port;
    }

    public ConnectionSettings settings() {
        return new ConnectionSettings("127.0.0.1", port, USER, PASSWORD);
    }

    public String uri() {
        return settings().uri();
    }

    /** Stops the broker, dropping every client; the port stays reserved for {@link #start()}. */
    public void stop() {
        server.close();
    }

    /** Starts a fresh broker (empty state) on the same port as before. */
    public void start() throws IOException {
        server = startBroker(port);
    }

    @Override
    public void close() {
        server.close();
    }
}
