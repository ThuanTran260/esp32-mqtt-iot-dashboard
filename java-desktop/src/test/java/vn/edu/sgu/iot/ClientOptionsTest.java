package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.sgu.iot.cli.ClientOptions;

class ClientOptionsTest {
    @TempDir
    Path dir;

    private static ClientOptions parse(String... args) {
        return ClientOptions.parse(args);
    }

    @Test
    void parsesPublishAndRejectsUnknownOption() {
        var options = parse("publish", "--config", "config/client.properties",
            "--topic", "sensor/led", "--message", "ON");
        assertEquals("publish", options.mode());
        assertEquals(Path.of("config/client.properties"), options.config());
        assertEquals("sensor/led", options.topic());
        assertEquals("ON", options.message());
        assertThrows(IllegalArgumentException.class,
            () -> parse("check", "--hosst", "192.168.43.100"));
    }

    @Test
    void subscribeDefaultsToSensorWildcardAndTenSeconds() {
        var options = parse("subscribe", "--config", "c.properties");
        assertEquals("sensor/#", options.topic());
        assertEquals(10, options.seconds());
    }

    @Test
    void rejectsDuplicateFlagMissingValueAndBadMode() {
        assertThrows(IllegalArgumentException.class,
            () -> parse("check", "--config", "a", "--config", "b"));
        assertThrows(IllegalArgumentException.class, () -> parse("check", "--config"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("publish", "--config", "a", "--topic", "--message", "ON"));
        assertThrows(IllegalArgumentException.class, () -> parse("explode", "--config", "a"));
        assertThrows(IllegalArgumentException.class, () -> parse());
        assertThrows(IllegalArgumentException.class, () -> parse("check", "extra"));
    }

    @Test
    void cliModesRequireConfigButDashboardDoesNot() {
        assertThrows(IllegalArgumentException.class, () -> parse("check"));
        assertThrows(IllegalArgumentException.class, () -> parse("subscribe"));
        assertNull(parse("dashboard").config());
    }

    @Test
    void publishNeedsTopicAndExactlyOneMessageSource() {
        assertThrows(IllegalArgumentException.class,
            () -> parse("publish", "--config", "a", "--message", "ON"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("publish", "--config", "a", "--topic", "sensor/led"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("publish", "--config", "a", "--topic", "sensor/led",
                "--message", "ON", "--message-file", "m.txt"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("publish", "--config", "a", "--topic", "sensor/#", "--message", "ON"));
    }

    @Test
    void secondsMustBeBetweenOneAndThreeThousandSixHundred() {
        assertEquals(3600, parse("subscribe", "--config", "a", "--seconds", "3600").seconds());
        assertThrows(IllegalArgumentException.class,
            () -> parse("subscribe", "--config", "a", "--seconds", "0"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("subscribe", "--config", "a", "--seconds", "3601"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("subscribe", "--config", "a", "--seconds", "abc"));
    }

    @Test
    void payloadFromMessageIsUtf8() throws IOException {
        var options = parse("publish", "--config", "a", "--topic", "t", "--message", "Bật đèn");
        assertArrayEquals("Bật đèn".getBytes(StandardCharsets.UTF_8), options.payload());
    }

    @Test
    void payloadFileIsReadAsUtf8AndLeadingBomIsDropped() throws IOException {
        Path file = dir.resolve("msg.txt");
        byte[] body = "{\"temp\":28.5}".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(file, withBom);
        var options = parse("publish", "--config", "a", "--topic", "t",
            "--message-file", file.toString());
        assertArrayEquals(body, options.payload());
    }

    @Test
    void payloadFileLargerThanLimitIsRejected() throws IOException {
        Path file = dir.resolve("big.txt");
        Files.write(file, new byte[ClientOptions.MAX_PAYLOAD_BYTES + 1]);
        var options = parse("publish", "--config", "a", "--topic", "t",
            "--message-file", file.toString());
        assertThrows(IOException.class, options::payload);
        Files.write(file, "x".repeat(ClientOptions.MAX_PAYLOAD_BYTES).getBytes(StandardCharsets.UTF_8));
        assertEquals(ClientOptions.MAX_PAYLOAD_BYTES, options.payload().length);
    }

    @Test
    void oversizedInlineMessageIsRejected() {
        var options = parse("publish", "--config", "a", "--topic", "t",
            "--message", "x".repeat(ClientOptions.MAX_PAYLOAD_BYTES + 1));
        assertThrows(IOException.class, options::payload);
    }

    @Test
    void missingPayloadFileIsAnIoError() {
        var options = parse("publish", "--config", "a", "--topic", "t",
            "--message-file", dir.resolve("nope.txt").toString());
        assertThrows(IOException.class, options::payload);
    }
}
