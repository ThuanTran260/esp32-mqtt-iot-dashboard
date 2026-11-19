package vn.edu.sgu.iot.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Where and how to reach the broker. The password never appears in {@link #toString()}. */
public record ConnectionSettings(String host, int port, String username, String password) {
    public static final int DEFAULT_PORT = 1883;
    public static final String DEFAULT_USERNAME = "user1";
    public static final String DEFAULT_PASSWORD = "123456";

    public ConnectionSettings {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        host = host == null ? "" : host.trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Broker host must not be empty");
        }
        if (host.equals("0.0.0.0")) {
            throw new IllegalArgumentException(
                "0.0.0.0 is a bind address; enter the real IPv4 address of the broker machine");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be 1..65535: " + port);
        }
    }

    public String uri() {
        return "tcp://" + host + ":" + port;
    }

    /**
     * Loads a {@code .properties} file with keys {@code host}, {@code port}, {@code username},
     * {@code password}. Only {@code host} is required; the rest take the demo defaults.
     *
     * @throws IOException on an unreadable file or an invalid value (message never has the password)
     */
    public static ConnectionSettings load(Path file) throws IOException {
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
        try {
            return new ConnectionSettings(
                props.getProperty("host"),
                port,
                props.getProperty("username", DEFAULT_USERNAME),
                props.getProperty("password", DEFAULT_PASSWORD));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid setting in " + file + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public String toString() {
        return "ConnectionSettings[host=" + host + ", port=" + port
            + ", username=" + username + ", password=***]";
    }
}
