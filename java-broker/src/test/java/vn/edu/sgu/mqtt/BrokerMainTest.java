package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.sgu.mqtt.server.BrokerMain;
import vn.edu.sgu.mqtt.server.BrokerServer;

class BrokerMainTest {
    @Test
    void helpExitsZeroAndMentionsConfig() {
        var out = new ByteArrayOutputStream();
        assertEquals(0, BrokerMain.run(new String[] {"--help"}, new PrintStream(out), System.err));
        assertTrue(out.toString().contains("--config"));
    }

    @Test
    void unknownFlagExitsTwo() {
        assertEquals(2, BrokerMain.run(new String[] {"--bogus"}, System.out,
            new PrintStream(new ByteArrayOutputStream())));
    }

    @Test
    void missingConfigFileExitsOneWithoutStackTrace(@TempDir Path dir) {
        var err = new ByteArrayOutputStream();
        assertEquals(1, BrokerMain.run(new String[] {"--config", dir.resolve("nope.properties").toString()},
            System.out, new PrintStream(err)));
        assertFalse(err.toString().contains("\tat "));
    }

    @Test
    void portInUseExitsOne(@TempDir Path dir) throws Exception {
        try (var busy = new ServerSocket(0)) {
            Path f = dir.resolve("b.properties");
            Files.writeString(f, "bindAddress=127.0.0.1\nport=" + busy.getLocalPort() + "\npassword=secret-xyz\n");
            var err = new ByteArrayOutputStream();
            assertEquals(1, BrokerMain.run(new String[] {"--config", f.toString()}, System.out,
                new PrintStream(err)));
            assertTrue(err.toString().contains(String.valueOf(busy.getLocalPort())));
            assertFalse(err.toString().contains("secret-xyz"));
        }
    }

    @Test
    void startsFromConfigAndPrintsNoPassword(@TempDir Path dir) throws Exception {
        int port;
        try (var probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Path f = dir.resolve("b.properties");
        Files.writeString(f, "bindAddress=127.0.0.1\nport=" + port + "\npassword=secret-xyz\n");
        var out = new ByteArrayOutputStream();
        BrokerServer server = BrokerMain.startFromArgs(new String[] {"--config", f.toString()},
            new PrintStream(out));
        try {
            var options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setUserName("user1");
            options.setPassword("secret-xyz".toCharArray());
            var client = new MqttClient("tcp://127.0.0.1:" + port, "main-test", new MemoryPersistence());
            client.connect(options);
            assertTrue(client.isConnected());
            client.disconnect();
            client.close();
            assertTrue(out.toString().contains("listening on 127.0.0.1:" + port));
            assertFalse(out.toString().contains("secret-xyz"));
        } finally {
            server.close();
        }
    }
}
