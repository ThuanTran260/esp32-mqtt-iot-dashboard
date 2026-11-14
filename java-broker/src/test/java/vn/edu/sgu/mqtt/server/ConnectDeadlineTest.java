package vn.edu.sgu.mqtt.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.edu.sgu.mqtt.RawClient;
import vn.edu.sgu.mqtt.RunningBroker;

@Timeout(30)
class ConnectDeadlineTest {
    private static BrokerServer shortDeadlineServer() throws Exception {
        var server = new BrokerServer(
            new BrokerConfig("127.0.0.1", 0, RunningBroker.USER, RunningBroker.PASSWORD),
            Duration.ofMillis(300));
        server.start();
        return server;
    }

    @Test
    void connectionThatNeverSendsConnectIsClosed() throws Exception {
        try (var server = shortDeadlineServer();
             var client = new RawClient(server.port())) {
            client.assertClosed();
        }
    }

    @Test
    void connectDeadlineNoLongerAppliesAfterConnackWhenKeepAliveIsZero() throws Exception {
        try (var server = shortDeadlineServer();
             var client = new RawClient(server.port())) {
            assertEquals(0, client.connectAndGetCode(RawClient.validConnect("idle", 0)));
            // Well past the 300 ms CONNECT deadline: the connection must still be open,
            // i.e. nothing arrives and the read just times out.
            client.socket().setSoTimeout(900);
            assertThrows(SocketTimeoutException.class, client::read);
        }
    }
}
