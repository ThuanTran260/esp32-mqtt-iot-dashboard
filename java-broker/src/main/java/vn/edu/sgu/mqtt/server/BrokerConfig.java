package vn.edu.sgu.mqtt.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Broker settings. The constructor accepts port 0 (ephemeral, for tests); a config
 * <em>file</em> must name a port in 1..65535.
 */
public record BrokerConfig(String bindAddress, int port, String username, String password) {
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    public static final int DEFAULT_PORT = 1883;
    public static final String DEFAULT_USERNAME = "user1";
    public static final String DEFAULT_PASSWORD = "123456";

    public BrokerConfig {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port out of range 0..65535: " + port);
        }
    }

    /**
     * Loads a {@code .properties} file with keys {@code bindAddress}, {@code port},
     * {@code username}, {@code password}; absent keys take the demo defaults.
     *
     * @throws IOException if the file cannot be read or the port is not an integer in 1..65535
     */
    public static BrokerConfig load(Path file) throws IOException {
        var props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        String rawPort = props.getProperty("port", String.valueOf(DEFAULT_PORT)).trim();
        int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid port in " + file + ": " + rawPort, ex);
        }
        if (port < 1 || port > 65535) {
            throw new IOException("Port in " + file + " must be 1..65535: " + port);
        }
        return new BrokerConfig(
            props.getProperty("bindAddress", DEFAULT_BIND_ADDRESS).trim(),
            port,
            props.getProperty("username", DEFAULT_USERNAME),
            props.getProperty("password", DEFAULT_PASSWORD));
    }

    /** Never reveals the password. */
    @Override
    public String toString() {
        return "BrokerConfig[bindAddress=" + bindAddress + ", port=" + port
            + ", username=" + username + ", password=***]";
    }
}
