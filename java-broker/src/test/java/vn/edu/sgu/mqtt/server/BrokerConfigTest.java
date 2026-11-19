package vn.edu.sgu.mqtt.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrokerConfigTest {
    @TempDir
    Path dir;

    private Path write(String text) throws IOException {
        return Files.writeString(dir.resolve("broker.properties"), text);
    }

    @Test
    void loadsAllKeysFromPropertiesFile() throws IOException {
        var config = BrokerConfig.load(write(
            "bindAddress=127.0.0.1\nport=1884\nusername=alice\npassword=s3cret\n"));
        assertEquals(new BrokerConfig("127.0.0.1", 1884, "alice", "s3cret"), config);
    }

    @Test
    void missingKeysFallBackToDemoDefaults() throws IOException {
        var config = BrokerConfig.load(write(""));
        assertEquals(new BrokerConfig("0.0.0.0", 1883, "user1", "123456"), config);
    }

    @Test
    void filePortMustBeInRange1To65535() throws IOException {
        assertThrows(IOException.class, () -> BrokerConfig.load(write("port=0\n")));
        assertThrows(IOException.class, () -> BrokerConfig.load(write("port=65536\n")));
        assertThrows(IOException.class, () -> BrokerConfig.load(write("port=abc\n")));
    }

    @Test
    void constructorAllowsPortZeroForTestsButNotOutOfRange() {
        assertEquals(0, new BrokerConfig("127.0.0.1", 0, "u", "p").port());
        assertThrows(IllegalArgumentException.class, () -> new BrokerConfig("127.0.0.1", 70000, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerConfig("127.0.0.1", -1, "u", "p"));
    }

    @Test
    void toStringNeverContainsThePassword() {
        assertFalse(new BrokerConfig("127.0.0.1", 0, "u", "hunter2").toString().contains("hunter2"));
    }
}
