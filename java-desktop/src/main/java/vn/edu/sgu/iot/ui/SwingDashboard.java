package vn.edu.sgu.iot.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;
import vn.edu.sgu.iot.mqtt.ConnectionState;
import vn.edu.sgu.iot.mqtt.MqttTransport;
import vn.edu.sgu.iot.mqtt.PahoTransport;

/** Plain Swing front end of {@link DashboardController}. All component access happens on the EDT. */
public final class SwingDashboard extends JFrame {
    private static final long CLOSE_WAIT_SECONDS = 3;
    private static final String NONE = "-";

    private final DashboardController controller;
    private final Runnable onClosed;

    private final JTextField hostField = new JTextField(14);
    private final JTextField portField = new JTextField(5);
    private final JTextField userField = new JTextField(8);
    private final JPasswordField passwordField = new JPasswordField(8);
    private final JButton connectButton = new JButton("Connect");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JLabel statusLabel = new JLabel("Not connected");

    private final JLabel temperatureLabel = new JLabel(NONE);
    private final JLabel humidityLabel = new JLabel(NONE);
    private final JLabel deviceLabel = new JLabel(NONE);
    private final JLabel brokerLabel = new JLabel(NONE);
    private final JLabel updateLabel = new JLabel(NONE);

    private final JButton ledOnButton = new JButton("LED ON");
    private final JButton ledOffButton = new JButton("LED OFF");
    private final JTextArea logArea = new JTextArea(10, 50);

    private boolean closing;

    /**
     * Must be called on the EDT.
     *
     * @param prefill optional values for the form; the window never connects by itself
     * @param onClosed run once after the window has been disposed
     */
    public SwingDashboard(MqttTransport transport, ConnectionSettings prefill, Runnable onClosed) {
        super("ESP32 MQTT Dashboard");
        this.onClosed = onClosed;
        this.controller = new DashboardController(transport, SwingUtilities::invokeLater);
        controller.setOnChange(this::render);

        hostField.setText(prefill == null ? "" : prefill.host());
        portField.setText(String.valueOf(prefill == null ? ConnectionSettings.DEFAULT_PORT : prefill.port()));
        userField.setText(prefill == null ? ConnectionSettings.DEFAULT_USERNAME : prefill.username());
        passwordField.setText(prefill == null ? ConnectionSettings.DEFAULT_PASSWORD : prefill.password());

        logArea.setEditable(false);
        logArea.setLineWrap(true);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }
        });

        connectButton.addActionListener(event -> onConnect());
        disconnectButton.addActionListener(event -> controller.disconnect());
        ledOnButton.addActionListener(event -> controller.setLedOn(true));
        ledOffButton.addActionListener(event -> controller.setLedOn(false));

        var content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(buildTop(), BorderLayout.NORTH);
        content.add(buildCenter(), BorderLayout.CENTER);
        content.add(buildBottom(), BorderLayout.SOUTH);
        setContentPane(content);

        render(controller.snapshot());
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Opens the window with a real {@link PahoTransport} and blocks until it is closed.
     *
     * @return 0 after a normal close, 1 when the window could not be created
     */
    public static int open(ConnectionSettings prefill, PrintStream err) {
        var closed = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeLater(() -> {
            try {
                new SwingDashboard(new PahoTransport(), prefill, closed::countDown).setVisible(true);
            } catch (RuntimeException | Error ex) {
                failure.set(ex);
                closed.countDown();
            }
        });
        try {
            closed.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 1;
        }
        Throwable error = failure.get();
        if (error != null) {
            err.println("Cannot open the dashboard window: " + error.getClass().getSimpleName());
            return 1;
        }
        return 0;
    }

    private JPanel buildTop() {
        var panel = new JPanel(new GridBagLayout());
        var c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        addField(panel, c, "Host", hostField);
        addField(panel, c, "Port", portField);
        addField(panel, c, "Username", userField);
        addField(panel, c, "Password", passwordField);
        panel.add(connectButton, c);
        panel.add(disconnectButton, c);
        var statusRow = new GridBagConstraints();
        statusRow.gridy = 1;
        statusRow.gridwidth = GridBagConstraints.REMAINDER;
        statusRow.anchor = GridBagConstraints.WEST;
        statusRow.insets = new Insets(4, 4, 2, 4);
        panel.add(statusLabel, statusRow);
        return panel;
    }

    private static void addField(JPanel panel, GridBagConstraints c, String label, java.awt.Component field) {
        panel.add(new JLabel(label), c);
        panel.add(field, c);
    }

    private JPanel buildCenter() {
        var panel = new JPanel(new GridLayout(0, 2, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Readings"));
        panel.add(new JLabel("Temperature"));
        panel.add(temperatureLabel);
        panel.add(new JLabel("Humidity"));
        panel.add(humidityLabel);
        panel.add(new JLabel("Device"));
        panel.add(deviceLabel);
        panel.add(new JLabel("Broker"));
        panel.add(brokerLabel);
        panel.add(new JLabel("Last update"));
        panel.add(updateLabel);
        return panel;
    }

    private JPanel buildBottom() {
        var buttons = new JPanel();
        buttons.add(ledOnButton);
        buttons.add(ledOffButton);
        var panel = new JPanel(new BorderLayout(4, 4));
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void onConnect() {
        String portText = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            statusLabel.setText("Port must be a whole number between 1 and 65535");
            return;
        }
        ConnectionSettings settings;
        try {
            settings = new ConnectionSettings(hostField.getText(), port,
                userField.getText(), new String(passwordField.getPassword()));
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage()); // host and port only, never the password
            return;
        }
        controller.connect(settings);
    }

    /** Runs on the EDT: the controller delivers every change through the invokeLater executor. */
    private void render(DashboardController.Snapshot s) {
        ConnectionState broker = s.broker();
        boolean idle = broker == ConnectionState.DISCONNECTED || broker == ConnectionState.ERROR;
        boolean connected = broker == ConnectionState.CONNECTED;
        temperatureLabel.setText(s.temperature() == null ? NONE : s.temperature() + " °C");
        humidityLabel.setText(s.humidity() == null ? NONE : s.humidity() + " %");
        deviceLabel.setText(s.device().name());
        brokerLabel.setText(broker.name());
        updateLabel.setText(s.lastUpdate() == null ? NONE : s.lastUpdate().toString());
        statusLabel.setText(s.message());
        connectButton.setEnabled(idle && !closing);
        disconnectButton.setEnabled(!idle && !closing);
        ledOnButton.setEnabled(connected && !closing);
        ledOffButton.setEnabled(connected && !closing);
        for (var field : new JTextField[]{hostField, portField, userField, passwordField}) {
            field.setEnabled(idle && !closing);
        }
        logArea.setText(String.join("\n", s.log()));
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** EDT. Disconnects off the EDT, waits briefly, then disposes the window. */
    private void closeWindow() {
        if (closing) {
            return;
        }
        closing = true;
        render(controller.snapshot());
        var worker = new Thread(() -> {
            try {
                controller.close().toCompletableFuture().get(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                // timed out or failed: the process is about to end anyway
            }
            SwingUtilities.invokeLater(() -> {
                dispose();
                onClosed.run();
            });
        }, "dashboard-close");
        worker.setDaemon(true);
        worker.start();
    }
}
