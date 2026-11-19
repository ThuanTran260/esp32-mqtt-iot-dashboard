package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.sgu.iot.mqtt.ConnectionSettings;

class ClientMainTest {
    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private int run(String... args) {
        return ClientMain.run(args, out, err);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    /** Dashboard run with a fake launcher: no window is ever created. */
    private int runDashboard(List<ConnectionSettings> launched, int launcherCode, boolean headless,
                             String... args) {
        return ClientMain.run(args, out, err, (prefill, e) -> {
            launched.add(prefill);
            return launcherCode;
        }, () -> headless);
    }

    @Test
    void helpPrintsUsageAndExitsZero() {
        assertEquals(0, run("--help"));
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("Usage:"));
        assertEquals(0, run("-h"));
    }

    @Test
    void usageErrorsExitTwoWithHint() {
        assertEquals(2, run("check", "--hosst", "x"));
        var text = err();
        assertTrue(text.contains("--hosst"), text);
        assertTrue(text.contains("--help"), text);
        assertEquals(2, run());
    }

    @Test
    void dashboardWithoutConfigLaunchesWithEmptyPrefill() {
        var launched = new ArrayList<ConnectionSettings>();
        assertEquals(0, runDashboard(launched, 0, false, "dashboard"));
        assertEquals(1, launched.size());
        assertNull(launched.get(0));
    }

    @Test
    void dashboardPrefillsFromConfigAndPropagatesLauncherExitCode() throws IOException {
        Path config = dir.resolve("client.properties");
        Files.writeString(config, "host=192.168.43.100\nport=1884\nusername=u\npassword=s3cret\n");
        var launched = new ArrayList<ConnectionSettings>();
        assertEquals(1, runDashboard(launched, 1, false, "dashboard", "--config", config.toString()));
        assertEquals("192.168.43.100", launched.get(0).host());
        assertEquals(1884, launched.get(0).port());
        assertFalse(err().contains("s3cret"));
    }

    @Test
    void dashboardWithMissingConfigExitsOneWithoutLaunching() {
        var launched = new ArrayList<ConnectionSettings>();
        int code = runDashboard(launched, 0, false,
            "dashboard", "--config", dir.resolve("none.properties").toString());
        assertEquals(1, code);
        assertTrue(launched.isEmpty());
        assertTrue(err().contains("none.properties"), err());
    }

    @Test
    void dashboardHeadlessPrintsOneLineAndExitsOne() {
        var launched = new ArrayList<ConnectionSettings>();
        assertEquals(1, runDashboard(launched, 0, true, "dashboard"));
        assertTrue(launched.isEmpty());
        assertEquals(1, err().strip().lines().count(), err());
    }
}
