package vn.edu.sgu.mqtt.routing;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import vn.edu.sgu.mqtt.protocol.ProtocolException;

/** MQTT 3.1.1 topic name / topic filter validation and wildcard matching. */
public final class TopicFilter {
    private static final int MAX_ENCODED = 65535;

    private TopicFilter() {
    }

    /** Validates a topic name used in PUBLISH or Will: non-empty, no NUL, no wildcards. */
    public static void validateTopic(String topic) throws ProtocolException {
        validateString(topic, "Topic");
        if (topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
            throw new ProtocolException("Wildcard in topic name");
        }
    }

    /** Validates a subscription filter: {@code #} only as the whole last level, {@code +} as a whole level. */
    public static void validateFilter(String filter) throws ProtocolException {
        validateString(filter, "Topic filter");
        String[] levels = filter.split("/", -1);
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            if (level.indexOf('#') >= 0) {
                if (!level.equals("#") || i != levels.length - 1) {
                    throw new ProtocolException("Invalid '#' in topic filter");
                }
            } else if (level.indexOf('+') >= 0 && !level.equals("+")) {
                throw new ProtocolException("Invalid '+' in topic filter");
            }
        }
    }

    /** Matches a valid filter against a valid topic name; {@code $}-topics skip a leading wildcard. */
    public static boolean matches(String filter, String topic) {
        if (topic.startsWith("$") && (filter.startsWith("#") || filter.startsWith("+"))) {
            return false;
        }
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        int i = 0;
        for (; i < f.length; i++) {
            if (f[i].equals("#")) return true;
            if (i >= t.length) return false;
            if (!f[i].equals("+") && !f[i].equals(t[i])) return false;
        }
        return i == t.length;
    }

    private static void validateString(String value, String what) throws ProtocolException {
        if (value == null || value.isEmpty()) {
            throw new ProtocolException(what + " must not be empty");
        }
        if (value.indexOf('\0') >= 0) {
            throw new ProtocolException("NUL in MQTT string");
        }
        // Cheap bound first: every UTF-16 unit encodes to at least one UTF-8 byte.
        if (value.length() > MAX_ENCODED) {
            throw new ProtocolException(what + " too long");
        }
        var encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer encoded = encoder.encode(CharBuffer.wrap(value));
            if (encoded.remaining() > MAX_ENCODED) {
                throw new ProtocolException(what + " too long");
            }
        } catch (CharacterCodingException ex) {
            throw new ProtocolException("Invalid UTF-16 in MQTT string");
        }
    }
}
