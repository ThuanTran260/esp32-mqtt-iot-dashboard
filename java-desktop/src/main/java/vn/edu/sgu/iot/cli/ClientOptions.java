package vn.edu.sgu.iot.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed command line: {@code <mode> [--config f] [--topic t] [--message m | --message-file f]
 * [--seconds n]}. Parsing never touches the network or the file system.
 */
public record ClientOptions(String mode, Path config, String topic, String message,
                            Path messageFile, int seconds) {
    public static final int MAX_PAYLOAD_BYTES = 65536;
    public static final int MAX_SECONDS = 3600;
    public static final int DEFAULT_SUBSCRIBE_SECONDS = 10;
    public static final String DEFAULT_SUBSCRIBE_TOPIC = "sensor/#";

    private static final List<String> MODES = List.of("dashboard", "check", "publish", "subscribe");
    private static final Set<String> FLAGS =
        Set.of("--config", "--topic", "--message", "--message-file", "--seconds");

    /** @throws IllegalArgumentException with a one-line usage message */
    public static ClientOptions parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing mode (one of " + String.join(", ", MODES) + ")");
        }
        String mode = args[0];
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String flag = args[i];
            if (!FLAGS.contains(flag)) {
                throw new IllegalArgumentException(
                    flag.startsWith("--") ? "Unknown option: " + flag : "Unexpected argument: " + flag);
            }
            if (values.containsKey(flag)) {
                throw new IllegalArgumentException("Duplicate option: " + flag);
            }
            if (i + 1 >= args.length || FLAGS.contains(args[i + 1])) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            values.put(flag, args[++i]);
        }

        boolean publish = mode.equals("publish");
        if (!publish && (values.containsKey("--message") || values.containsKey("--message-file"))) {
            throw new IllegalArgumentException("--message and --message-file are only valid with publish");
        }
        if (!mode.equals("dashboard") && !values.containsKey("--config")) {
            throw new IllegalArgumentException("--config is required for " + mode);
        }

        String topic = values.get("--topic");
        if (publish) {
            if (topic == null) {
                throw new IllegalArgumentException("publish requires --topic");
            }
            if (topic.isEmpty() || topic.contains("#") || topic.contains("+")) {
                throw new IllegalArgumentException("publish topic must be a concrete topic without wildcards");
            }
            if (values.containsKey("--message") == values.containsKey("--message-file")) {
                throw new IllegalArgumentException("publish requires exactly one of --message or --message-file");
            }
        } else if (mode.equals("subscribe")) {
            if (topic == null) {
                topic = DEFAULT_SUBSCRIBE_TOPIC;
            } else if (topic.isEmpty()) {
                throw new IllegalArgumentException("--topic must not be empty");
            }
        }

        int seconds = DEFAULT_SUBSCRIBE_SECONDS;
        if (values.containsKey("--seconds")) {
            try {
                seconds = Integer.parseInt(values.get("--seconds"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("--seconds must be a whole number: " + values.get("--seconds"));
            }
            if (seconds < 1 || seconds > MAX_SECONDS) {
                throw new IllegalArgumentException("--seconds must be between 1 and " + MAX_SECONDS);
            }
        }

        String config = values.get("--config");
        String messageFile = values.get("--message-file");
        return new ClientOptions(mode,
            config == null ? null : Path.of(config),
            topic,
            values.get("--message"),
            messageFile == null ? null : Path.of(messageFile),
            seconds);
    }

    /**
     * The bytes to publish: {@code --message} as UTF-8, or the UTF-8 content of {@code --message-file}
     * without a leading byte order mark. At most {@value #MAX_PAYLOAD_BYTES} bytes.
     */
    public byte[] payload() throws IOException {
        byte[] bytes;
        if (messageFile != null) {
            long size = Files.size(messageFile);
            if (size > MAX_PAYLOAD_BYTES) {
                throw tooLarge();
            }
            String text = Files.readString(messageFile, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            bytes = text.getBytes(StandardCharsets.UTF_8);
        } else if (message != null) {
            bytes = message.getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IOException("No message to publish");
        }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw tooLarge();
        }
        return bytes;
    }

    private static IOException tooLarge() {
        return new IOException("Message is larger than " + MAX_PAYLOAD_BYTES + " bytes");
    }
}
