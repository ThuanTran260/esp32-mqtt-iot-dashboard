package vn.edu.sgu.mqtt.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.mqtt.server.ClientSession.CloseReason;

class ClientRegistryTest {
    private final BrokerConfig config = new BrokerConfig("127.0.0.1", 0, "u", "p");
    private final ClientRegistry registry = new ClientRegistry();

    private ClientSession session() {
        return new ClientSession(new Socket(), config, registry);
    }

    @Test
    void registerReplacesAndClosesTheOldSessionAsReplaced() throws IOException {
        var old = session();
        var replacement = session();
        registry.register("id", old);
        registry.register("id", replacement);
        assertEquals(CloseReason.REPLACED, old.closeReason());
        assertEquals(List.of(replacement), registry.snapshot());
        assertNull(replacement.closeReason());
    }

    @Test
    void removeWithStaleSessionKeepsTheNewerOne() {
        var old = session();
        var newer = session();
        registry.register("id", old);
        registry.register("id", newer);
        registry.remove("id", old);
        assertEquals(List.of(newer), registry.snapshot());
        registry.remove("id", newer);
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void snapshotIsACopy() {
        var a = session();
        registry.register("a", a);
        var snapshot = registry.snapshot();
        registry.remove("a", a);
        assertSame(a, snapshot.get(0));
        assertTrue(registry.snapshot().isEmpty());
    }
}
