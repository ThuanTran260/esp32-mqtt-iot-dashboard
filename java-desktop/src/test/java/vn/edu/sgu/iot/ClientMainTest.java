package vn.edu.sgu.iot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClientMainTest {
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private int run(String... args) {
        return ClientMain.run(args, out, err);
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
        var text = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("--hosst"), text);
        assertTrue(text.contains("--help"), text);
        assertEquals(2, run());
    }

    @Test
    void dashboardExitsTwoWithoutNetwork() {
        assertEquals(2, run("dashboard"));
        assertTrue(errBytes.toString(StandardCharsets.UTF_8)
            .contains("Dashboard UI is not available yet; use check/subscribe/publish"));
    }
}
