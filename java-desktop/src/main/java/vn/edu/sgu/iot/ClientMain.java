package vn.edu.sgu.iot;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import vn.edu.sgu.iot.cli.ClientCommands;
import vn.edu.sgu.iot.cli.ClientOptions;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.MqttTransport;
import vn.edu.sgu.iot.mqtt.PahoTransport;
import vn.edu.sgu.iot.ui.SwingDashboard;

/** Entry point of the client JAR. Only {@link #main} turns a return code into a process exit. */
public final class ClientMain {
    static final String USAGE = String.join(System.lineSeparator(),
        "Usage:",
        "  java -jar java-desktop-1.0-SNAPSHOT.jar dashboard [--config <file>]",
        "  java -jar java-desktop-1.0-SNAPSHOT.jar check --config <file>",
        "  java -jar java-desktop-1.0-SNAPSHOT.jar subscribe --config <file> [--topic sensor/#] [--seconds 10]",
        "  java -jar java-desktop-1.0-SNAPSHOT.jar publish --config <file> --topic <topic>"
            + " (--message <text> | --message-file <file>)",
        "",
        "Options:",
        "  --config <file>        properties file with host, port, username, password"
            + " (optional for dashboard: pre-fills the form)",
        "  --topic <topic>        subscribe: topic filter (default sensor/#); publish: target topic",
        "  --message <text>       payload to publish (UTF-8)",
        "  --message-file <file>  publish the UTF-8 content of a file (max 65536 bytes)",
        "  --seconds <1..3600>    how long subscribe listens (default 10)",
        "  -h, --help             show this help",
        "",
        "Exit codes: 0 ok, 1 connection/config/publish failure, 2 usage error.");

    /** Opens the dashboard window and blocks until it is closed. Tests inject a fake. */
    @FunctionalInterface
    interface DashboardLauncher {
        /**
         * @param prefill values for the form, or null
         * @return the exit code
         */
        int launch(ConnectionSettings prefill, PrintStream err);
    }

    private ClientMain() {
    }

    public static void main(String[] args) {
        var stop = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        // Ctrl+C: end subscribe early and give main a moment to send DISCONNECT before the JVM dies.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.countDown();
            try {
                finished.await(4, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "cli-shutdown"));
        int code = run(args, System.out, System.err, stop);
        finished.countDown();
        System.exit(code);
    }

    /** Testable core of {@link #main}; returns the exit code. Opens a real window for "dashboard". */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, new CountDownLatch(1));
    }

    static int run(String[] args, PrintStream out, PrintStream err, CountDownLatch stop) {
        return run(args, out, err, stop, SwingDashboard::open, GraphicsEnvironment::isHeadless);
    }

    /** Same as {@link #run(String[], PrintStream, PrintStream)} with the window launcher injected. */
    static int run(String[] args, PrintStream out, PrintStream err, DashboardLauncher launcher,
                   BooleanSupplier headless) {
        return run(args, out, err, new CountDownLatch(1), launcher, headless);
    }

    static int run(String[] args, PrintStream out, PrintStream err, CountDownLatch stop,
                   DashboardLauncher launcher, BooleanSupplier headless) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            out.println(USAGE);
            return 0;
        }
        ClientOptions options;
        try {
            options = ClientOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            err.println("error: " + ex.getMessage());
            err.println("Run with --help for usage.");
            return 2;
        }
        if (options.mode().equals("dashboard")) {
            return runDashboard(options, err, launcher, headless);
        }
        MqttTransport transport = options.mode().equals("subscribe")
            ? new PahoTransport(Map.of(options.topic(), 0))
            : new PahoTransport();
        return ClientCommands.run(options, transport, out, err, stop);
    }

    private static int runDashboard(ClientOptions options, PrintStream err, DashboardLauncher launcher,
                                    BooleanSupplier headless) {
        if (headless.getAsBoolean()) {
            err.println("The dashboard needs a graphical display; use check, subscribe or publish here.");
            return 1;
        }
        ConnectionSettings prefill = null;
        if (options.config() != null) {
            try {
                prefill = ConnectionSettings.load(options.config());
            } catch (NoSuchFileException ex) {
                err.println("File not found: " + ex.getFile());
                return 1;
            } catch (IOException ex) {
                String text = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                err.println(text.replace("\r", " ").replace("\n", " "));
                return 1;
            }
        }
        return launcher.launch(prefill, err);
    }
}
