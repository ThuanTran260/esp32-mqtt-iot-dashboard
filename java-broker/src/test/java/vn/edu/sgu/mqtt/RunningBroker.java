package vn.edu.sgu.mqtt;

import java.io.IOException;
import vn.edu.sgu.mqtt.server.BrokerConfig;
import vn.edu.sgu.mqtt.server.BrokerServer;

/** Test fixture: a started broker on a loopback ephemeral port. */
public final class RunningBroker implements AutoCloseable {
    public static final String USER = "user1";
    public static final String PASSWORD = "123456";

    private final BrokerServer server;

    private RunningBroker(BrokerServer server) {
        this.server = server;
    }

    public static RunningBroker open() throws IOException {
        var server = new BrokerServer(new BrokerConfig("127.0.0.1", 0, USER, PASSWORD));
        server.start();
        return new RunningBroker(server);
    }

    public BrokerServer server() {
        return server;
    }

    public int port() {
        return server.port();
    }

    public String uri() {
        return "tcp://127.0.0.1:" + server.port();
    }

    @Override
    public void close() {
        server.close();
    }
}
