package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import vn.edu.sgu.mqtt.protocol.ProtocolException;
import vn.edu.sgu.mqtt.routing.TopicFilter;

class TopicFilterTest {

    @Test
    void matchesLevelsIncludingEmptyAndDollarTopics() throws Exception {
        assertTrue(TopicFilter.matches("sensor/+", "sensor/data"));
        assertTrue(TopicFilter.matches("sensor/#", "sensor"));
        assertTrue(TopicFilter.matches("sensor/+", "sensor/"));
        assertFalse(TopicFilter.matches("#", "$SYS/status"));
        assertThrows(ProtocolException.class,
            () -> TopicFilter.validateFilter("sensor/#/data"));
    }

    @Test
    void exactAndMultiLevelMatching() {
        assertTrue(TopicFilter.matches("sensor/data", "sensor/data"));
        assertFalse(TopicFilter.matches("sensor/data", "sensor/led"));
        assertFalse(TopicFilter.matches("sensor/data", "sensor"));
        assertFalse(TopicFilter.matches("sensor", "sensor/data"));
        assertTrue(TopicFilter.matches("#", "sensor/data"));
        assertTrue(TopicFilter.matches("sensor/#", "sensor/data/extra"));
        assertTrue(TopicFilter.matches("+/data", "sensor/data"));
        assertFalse(TopicFilter.matches("+/data", "sensor/led"));
        assertFalse(TopicFilter.matches("+", "sensor/data"));
        assertTrue(TopicFilter.matches("+/+", "/"));
        assertTrue(TopicFilter.matches("/finance", "/finance"));
        assertFalse(TopicFilter.matches("+/finance", "/finance/x"));
    }

    @Test
    void dollarTopicsOnlyMatchExplicitFirstLevel() {
        assertFalse(TopicFilter.matches("+/status", "$SYS/status"));
        assertTrue(TopicFilter.matches("$SYS/#", "$SYS/status"));
        assertTrue(TopicFilter.matches("$SYS/+", "$SYS/status"));
    }

    @Test
    void validatesFilters() {
        assertDoesNotThrow(() -> TopicFilter.validateFilter("sensor/+"));
        assertDoesNotThrow(() -> TopicFilter.validateFilter("sensor/#"));
        assertDoesNotThrow(() -> TopicFilter.validateFilter("#"));
        assertDoesNotThrow(() -> TopicFilter.validateFilter("+"));
        assertDoesNotThrow(() -> TopicFilter.validateFilter("+/+/data"));
        assertDoesNotThrow(() -> TopicFilter.validateFilter("/"));
        for (String bad : new String[] {
            "", "sensor/#/data", "sensor#", "sensor/a#", "#/x", "sensor+", "sensor/+a",
            "a+/b", "sensor/\0", "\0", "sensor/##"}) {
            assertThrows(ProtocolException.class, () -> TopicFilter.validateFilter(bad),
                "filter should be rejected: " + bad.replace("\0", "<NUL>"));
        }
    }

    @Test
    void validatesTopics() {
        assertDoesNotThrow(() -> TopicFilter.validateTopic("sensor/data"));
        assertDoesNotThrow(() -> TopicFilter.validateTopic("/"));
        assertDoesNotThrow(() -> TopicFilter.validateTopic("$SYS/status"));
        for (String bad : new String[] {"", "sensor/+", "sensor/#", "#", "+", "a\0b"}) {
            assertThrows(ProtocolException.class, () -> TopicFilter.validateTopic(bad),
                "topic should be rejected: " + bad.replace("\0", "<NUL>"));
        }
    }

    @Test
    void rejectsStringsOverEncodedLimit() {
        String max = "a".repeat(65535);
        String over = "a".repeat(65536);
        assertDoesNotThrow(() -> TopicFilter.validateTopic(max));
        assertDoesNotThrow(() -> TopicFilter.validateFilter(max));
        assertThrows(ProtocolException.class, () -> TopicFilter.validateTopic(over));
        assertThrows(ProtocolException.class, () -> TopicFilter.validateFilter(over));
        // 32768 two-byte characters = 65536 UTF-8 bytes.
        String multiByte = "é".repeat(32768);
        assertThrows(ProtocolException.class, () -> TopicFilter.validateTopic(multiByte));
    }

    @Test
    void rejectsUnpairedSurrogate() {
        assertThrows(ProtocolException.class, () -> TopicFilter.validateTopic("a\ud800b"));
    }
}
