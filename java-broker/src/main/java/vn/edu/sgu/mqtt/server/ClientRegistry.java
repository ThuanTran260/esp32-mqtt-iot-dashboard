package vn.edu.sgu.mqtt.server;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import vn.edu.sgu.mqtt.server.ClientSession.CloseReason;

/** Connected sessions by ClientId. A second CONNECT with the same id replaces the first. */
public final class ClientRegistry {
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    /**
     * Atomically installs {@code replacement} for {@code id}; a previous session is closed as
     * {@link CloseReason#REPLACED} after the map update, outside any registry synchronization.
     */
    public void register(String id, ClientSession replacement) {
        ClientSession previous = sessions.put(id, replacement);
        if (previous != null && previous != replacement) {
            previous.close(CloseReason.REPLACED);
        }
    }

    /** Removes {@code id} only if it still maps to {@code expected}; a newer session survives. */
    public void remove(String id, ClientSession expected) {
        sessions.remove(id, expected);
    }

    public List<ClientSession> snapshot() {
        return List.copyOf(sessions.values());
    }
}
