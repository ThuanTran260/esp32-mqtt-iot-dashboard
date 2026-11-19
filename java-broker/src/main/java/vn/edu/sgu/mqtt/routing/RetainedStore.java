package vn.edu.sgu.mqtt.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Subscription;

/** One retained message per topic. Thread-safe; payload arrays are always copied. */
public final class RetainedStore {
    private final Map<String, Publish> byTopic = new LinkedHashMap<>();

    /** Stores a copy of {@code message}; an empty payload deletes the topic's retained message. */
    public synchronized void put(Publish message) {
        if (message.payload().length == 0) {
            byTopic.remove(message.topic());
            return;
        }
        byTopic.put(message.topic(), new Publish(message.topic(), message.payload().clone(),
            message.qos(), true, false, 0));
    }

    /**
     * Copies of every retained message matched by at least one subscription, each topic once
     * (the stored QoS is kept; the retain flag is true).
     */
    public synchronized List<Publish> matching(List<Subscription> subscriptions) {
        var result = new ArrayList<Publish>();
        for (Publish stored : byTopic.values()) {
            for (Subscription subscription : subscriptions) {
                if (TopicFilter.matches(subscription.filter(), stored.topic())) {
                    result.add(new Publish(stored.topic(), stored.payload().clone(),
                        stored.qos(), true, false, 0));
                    break;
                }
            }
        }
        return result;
    }

    public synchronized int size() {
        return byTopic.size();
    }
}
