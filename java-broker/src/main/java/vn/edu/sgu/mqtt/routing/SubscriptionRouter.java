package vn.edu.sgu.mqtt.routing;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscribe;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscription;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Unsubscribe;
import vn.edu.sgu.mqtt.server.ClientSession;
import vn.edu.sgu.mqtt.server.ClientSession.CloseReason;

/**
 * Subscription table shared by every session. MVP scope: all grants are QoS 0 and every
 * outbound PUBLISH is QoS 0 with packet id 0.
 *
 * <p>The router lock only protects the tables and the retained store and is used to compute
 * snapshots; every socket write happens after the lock is released.
 */
public final class SubscriptionRouter {
    private static final Logger LOG = Logger.getLogger(SubscriptionRouter.class.getName());

    private final RetainedStore retained;
    /** Session identity → set of subscribed filters. Guarded by this. */
    private final Map<ClientSession, Set<String>> subscriptions = new IdentityHashMap<>();

    public SubscriptionRouter(RetainedStore retained) {
        this.retained = retained;
    }

    /** Registers the filters, acknowledges with QoS 0 grants, then replays retained messages. */
    public void subscribe(ClientSession session, Subscribe request) {
        List<Publish> replay;
        synchronized (this) {
            if (session.isClosed()) return; // cleanup already ran; do not leak an entry
            Set<String> filters = subscriptions.computeIfAbsent(session, s -> new LinkedHashSet<>());
            for (Subscription subscription : request.filters()) filters.add(subscription.filter());
            replay = retained.matching(request.filters());
        }
        List<Integer> grants = new ArrayList<>(request.filters().size());
        for (int i = 0; i < request.filters().size(); i++) grants.add(0);
        if (!session.enqueue(MqttCodec.suback(request.packetId(), grants))) return;
        for (Publish stored : replay) {
            if (!session.enqueue(MqttCodec.publish(outbound(stored, true)))) return;
        }
    }

    /** Removes the filters and acknowledges. */
    public void unsubscribe(ClientSession session, Unsubscribe request) {
        synchronized (this) {
            Set<String> filters = subscriptions.get(session);
            if (filters != null) {
                filters.removeAll(request.filters());
                if (filters.isEmpty()) subscriptions.remove(session);
            }
        }
        session.enqueue(MqttCodec.unsuback(request.packetId()));
    }

    /** Drops every subscription of a closed session. */
    public synchronized void remove(ClientSession session) {
        subscriptions.remove(session);
    }

    /** Number of sessions that currently hold at least one subscription. */
    public synchronized int subscriberCount() {
        return subscriptions.size();
    }

    /**
     * Stores the message when retained, then delivers one QoS 0 copy (retain=false) to every
     * session with a matching filter. A failing target is closed by its own send path and
     * never affects the publisher or the other targets.
     */
    public void route(Publish message) {
        List<ClientSession> targets = new ArrayList<>();
        synchronized (this) {
            if (message.retain()) retained.put(message);
            for (Map.Entry<ClientSession, Set<String>> entry : subscriptions.entrySet()) {
                for (String filter : entry.getValue()) {
                    if (TopicFilter.matches(filter, message.topic())) {
                        targets.add(entry.getKey());
                        break; // one copy per client even with overlapping filters
                    }
                }
            }
        }
        for (ClientSession target : targets) {
            try {
                target.sendPublish(outbound(message, false));
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Delivery to {0} failed: {1}",
                    new Object[] {target, ex.toString()});
                target.close(CloseReason.NETWORK);
            }
        }
    }

    private static Publish outbound(Publish message, boolean retain) {
        return new Publish(message.topic(), message.payload().clone(), 0, retain, false, 0);
    }
}
