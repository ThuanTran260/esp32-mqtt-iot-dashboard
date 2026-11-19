package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;

class ConnectionSettingsTest {
    @Test
    void buildsUriAndHidesPassword() {
        var s = new ConnectionSettings("192.168.1.10", 1883, "user1", "secret-pw");
        assertEquals("tcp://192.168.1.10:1883", s.uri());
        assertFalse(s.toString().contains("secret-pw"));
    }

    @Test
    void rejectsBadHostAndPort() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("", 1883, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("  ", 1883, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings(null, 1883, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("0.0.0.0", 1883, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("h", 0, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("h", 65536, "u", "p"));
    }

    @Test
    void loadsPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("client.properties");
        Files.writeString(file, "host=10.0.0.5\nport=1884\nusername=bob\npassword=pw\n");
        var s = ConnectionSettings.load(file);
        assertEquals(new ConnectionSettings("10.0.0.5", 1884, "bob", "pw"), s);
    }

    @Test
    void loadAppliesDefaultsAndReportsErrorsWithoutPassword(@TempDir Path dir) throws IOException {
        Path ok = dir.resolve("ok.properties");
        Files.writeString(ok, "host=10.0.0.5\n");
        assertEquals(new ConnectionSettings("10.0.0.5", 1883, "user1", "123456"), ConnectionSettings.load(ok));

        Path noHost = dir.resolve("nohost.properties");
        Files.writeString(noHost, "password=leaky-secret\n");
        var ex = assertThrows(IOException.class, () -> ConnectionSettings.load(noHost));
        assertFalse(ex.getMessage().contains("leaky-secret"));

        Path badPort = dir.resolve("badport.properties");
        Files.writeString(badPort, "host=h\nport=abc\n");
        assertTrue(assertThrows(IOException.class, () -> ConnectionSettings.load(badPort))
            .getMessage().contains("port"));

        assertThrows(IOException.class, () -> ConnectionSettings.load(dir.resolve("missing.properties")));
    }
}
