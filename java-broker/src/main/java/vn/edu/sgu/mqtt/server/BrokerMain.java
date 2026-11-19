package vn.edu.sgu.mqtt.server;

import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Command-line entry point: {@code java -jar java-broker.jar --config <file>}. */
public final class BrokerMain {
    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
        Usage: java -jar java-broker.jar --config <file>
               java -jar java-broker.jar --help

        Options:
          --config <file>  Broker settings (.properties): bindAddress, port, username, password
          --help           Show this message
        """;

    private BrokerMain() {}

    /** Thrown for malformed command lines; mapped to exit code 2. */
    private static final class UsageException extends IllegalArgumentException {
        UsageException(String message) {
            super(message);
        }
    }

    /** Parsed command line: {@code help} or a config path. */
    private record Options(boolean help, Path config) {}

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != EXIT_OK) System.exit(code);
    }

    /**
     * Runs the broker. Returns 0 for {@code --help} (or after a clean stop), 1 for config/bind
     * errors, 2 for bad arguments. On a successful start it blocks until the JVM is stopped.
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = parse(args);
        } catch (UsageException ex) {
            err.println("Error: " + ex.getMessage());
            err.print(USAGE);
            return EXIT_USAGE;
        }
        if (options.help()) {
            out.print(USAGE);
            return EXIT_OK;
        }

        BrokerConfig config;
        try {
            config = BrokerConfig.load(options.config());
        } catch (IOException | IllegalArgumentException ex) {
            err.println("Error: " + describeConfigProblem(options.config(), ex));
            return EXIT_ERROR;
        }

        BrokerServer server;
        try {
            server = start(config, out);
        } catch (BindException ex) {
            err.println("Error: " + describeBindProblem(config, ex));
            return EXIT_ERROR;
        } catch (IOException | IllegalArgumentException ex) {
            err.println("Error: Cannot start broker on " + config.bindAddress() + ":" + config.port()
                + ": " + ex.getMessage());
            return EXIT_ERROR;
        }

        var stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stopped.countDown();
        }, "broker-shutdown"));
        try {
            stopped.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return EXIT_OK;
    }

    /**
     * Parses {@code --config <file>}, loads the config and starts the broker; used by tests.
     *
     * @throws IOException on unreadable config or bind failure
     * @throws IllegalArgumentException on bad arguments or invalid settings
     */
    public static BrokerServer startFromArgs(String[] args, PrintStream out) throws IOException {
        Options options = parse(args);
        if (options.help()) throw new UsageException("--help does not start the broker");
        return start(BrokerConfig.load(options.config()), out);
    }

    private static Options parse(String[] args) {
        Path config = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    return new Options(true, null);
                }
                case "--config" -> {
                    if (i + 1 >= args.length) throw new UsageException("--config needs a file path");
                    config = Path.of(args[++i]);
                }
                default -> throw new UsageException("Unknown argument: " + args[i]);
            }
        }
        if (config == null) throw new UsageException("Missing required --config <file>");
        return new Options(false, config);
    }

    private static BrokerServer start(BrokerConfig config, PrintStream out) throws IOException {
        var server = new BrokerServer(config);
        server.start();
        // Print only bind/port: never the config object or the password.
        out.println("Java MQTT broker listening on " + config.bindAddress() + ":" + server.port());
        out.println("Connect clients to one of:");
        var addresses = LanAddresses.ipv4();
        if (addresses.isEmpty()) {
            out.println("  (no active IPv4 network adapter found)");
        }
        for (String address : addresses) {
            out.println("  " + address);
        }
        return server;
    }

    private static String describeConfigProblem(Path file, Exception ex) {
        if (ex instanceof NoSuchFileException) return "Config file not found: " + file;
        return "Cannot load config " + file + ": " + ex.getMessage();
    }

    private static String describeBindProblem(BrokerConfig config, BindException ex) {
        String reason = String.valueOf(ex.getMessage());
        if (reason.contains("assign requested address")) {
            return "Cannot bind to address " + config.bindAddress()
                + ": it is not an address of this machine. Fix bindAddress in the config.";
        }
        return "Port " + config.port()
            + " is already in use. Stop the other program or change port in the config.";
    }
}
