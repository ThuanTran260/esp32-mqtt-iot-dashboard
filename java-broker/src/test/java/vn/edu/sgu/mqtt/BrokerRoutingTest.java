package vn.edu.sgu.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import vn.edu.sgu.mqtt.protocol.MqttCodec;
import vn.edu.sgu.mqtt.protocol.MqttMessages.Publish;
import vn.edu.sgu.mqtt.protocol.Packet;

class BrokerRoutingTest {
    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static MqttConnectOptions options() {
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setUserName("user1");
        options.setPassword("123456".toCharArray());
        return options;
    }

    private static MqttClient paho(RunningBroker running, String id) throws MqttException {
        return new MqttClient(running.uri(), id, new MemoryPersistence());
    }

    // ---------------------------------------------------------------- live routing (Paho)

    @Test
    void routesWildcardTelemetryAndStopsAfterUnsubscribe() throws Exception {
        try (var running = RunningBroker.open();
             var sub = new MqttClient(running.uri(), "sub", new MemoryPersistence());
             var pub = new MqttClient(running.uri(), "pub", new MemoryPersistence())) {
            var received = new LinkedBlockingQueue<String>();
            var options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName("user1");
            options.setPassword("123456".toCharArray());
            sub.connect(options);
            pub.connect(options);
            sub.subscribe("sensor/+", 0,
                (topic, msg) -> received.add(topic + ":" +
                    new String(msg.getPayload(), StandardCharsets.UTF_8)));
            pub.publish("sensor/data", "{\"temp\":28.5,\"hum\":65.0}".getBytes(
                StandardCharsets.UTF_8), 0, false);
            assertEquals("sensor/data:{\"temp\":28.5,\"hum\":65.0}",
                received.poll(3, TimeUnit.SECONDS));
            sub.unsubscribe("sensor/+");
            pub.publish("sensor/data", new byte[]{1}, 0, false);
            assertNull(received.poll(250, TimeUnit.MILLISECONDS));
            pub.disconnect();
            sub.disconnect();
        }
    }

    @Test
    void qos1PublishIsAcknowledgedAndDeliveredAtQos0() throws Exception {
        try (var running = RunningBroker.open();
             var sub = paho(running, "sub");
             var pub = paho(running, "pub")) {
            var received = new LinkedBlockingQueue<String>();
            var qos = new LinkedBlockingQueue<Integer>();
            sub.connect(options());
            pub.connect(options());
            // Requested QoS 1 is granted as 0, but the subscriber must still receive.
            sub.subscribe("sensor/led", 1, (topic, msg) -> {
                qos.add(msg.getQos());
                received.add(new String(msg.getPayload(), StandardCharsets.UTF_8));
            });
            pub.publish("sensor/led", utf8("ON"), 1, false); // blocks until PUBACK
            assertEquals("ON", received.poll(3, TimeUnit.SECONDS));
            assertEquals(0, qos.poll(3, TimeUnit.SECONDS));
            pub.disconnect();
            sub.disconnect();
        }
    }

    // ---------------------------------------------------------------- SUBACK / PUBACK (raw)

    @Test
    void everySubackGrantIsQosZero() throws Exception {
        try (var running = RunningBroker.open();
             var sub = RawClient.connected(running.port(), "sub", 60);
             var pub = RawClient.connected(running.port(), "pub", 60)) {
            assertArrayEquals(new byte[] {0, 0, 0},
                sub.subscribe(1, 1, "a/+", "b/#", "c"));
            assertArrayEquals(new byte[] {0}, sub.subscribe(2, 2, "d"));
            pub.publishQos0("c", utf8("x"), false);
            Publish got = sub.readPublish();
            assertEquals("c", got.topic());
            assertEquals(0, got.qos());
            assertEquals(0, got.packetId());
            assertFalse(got.retain());
        }
    }

    @Test
    void qos1InboundPublishGetsPubackAndIsRouted() throws Exception {
        try (var running = RunningBroker.open();
             var sub = RawClient.connected(running.port(), "sub", 60);
             var pub = RawClient.connected(running.port(), "pub", 60)) {
            sub.subscribe(1, "sensor/data");
            pub.publishQos1("sensor/data", utf8("42"), false, 77);
            Publish got = sub.readPublish();
            assertEquals("42", new String(got.payload(), StandardCharsets.UTF_8));
            assertEquals(0, got.qos());
        }
    }

    @Test
    void qos2InboundPublishClosesConnection() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60)) {
            pub.send(MqttCodec.publish(new Publish("a", utf8("x"), 2, false, false, 5)));
            pub.assertClosed();
        }
    }

    // ---------------------------------------------------------------- retained (raw)

    @Test
    void retainedMessageIsReplayedWithRetainFlagToLateSubscriber() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var sub = RawClient.connected(running.port(), "sub", 60)) {
            pub.publishQos1("sensor/status", utf8("ONLINE"), true, 1);
            sub.subscribe(1, "sensor/+");
            Publish replay = sub.readPublish();
            assertEquals("sensor/status", replay.topic());
            assertEquals("ONLINE", new String(replay.payload(), StandardCharsets.UTF_8));
            assertTrue(replay.retain());
            assertEquals(0, replay.qos());
            sub.assertNothingPendingBeforePing();

            // A live publication reaches the already-subscribed client with retain=false.
            pub.publishQos0("sensor/status", utf8("BUSY"), true);
            Publish live = sub.readPublish();
            assertEquals("BUSY", new String(live.payload(), StandardCharsets.UTF_8));
            assertFalse(live.retain());

            // ... while the store now holds the newest value for another late subscriber.
            try (var late = RawClient.connected(running.port(), "late", 60)) {
                late.subscribe(1, "sensor/status");
                Publish second = late.readPublish();
                assertEquals("BUSY", new String(second.payload(), StandardCharsets.UTF_8));
                assertTrue(second.retain());
            }
        }
    }

    @Test
    void emptyRetainedPayloadDeletesTheRetainedMessage() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var late = RawClient.connected(running.port(), "late", 60)) {
            pub.publishQos0("sensor/status", utf8("ONLINE"), true);
            pub.publishQos0("sensor/status", new byte[0], true);
            pub.assertNothingPendingBeforePing(); // broker handled both before we continue
            late.subscribe(1, "sensor/status");
            late.assertNothingPendingBeforePing();
        }
    }

    @Test
    void retainedMessageMatchingSeveralFiltersIsReplayedOnce() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var sub = RawClient.connected(running.port(), "sub", 60)) {
            pub.publishQos0("sensor/data", utf8("R"), true);
            pub.assertNothingPendingBeforePing();
            sub.subscribe(1, "sensor/+", "sensor/data", "#");
            assertEquals("R", new String(sub.readPublish().payload(), StandardCharsets.UTF_8));
            sub.assertNothingPendingBeforePing();
        }
    }

    // ---------------------------------------------------------------- matching rules (raw)

    @Test
    void overlappingFiltersDeliverOneCopyPerPublication() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var sub = RawClient.connected(running.port(), "sub", 60)) {
            sub.subscribe(1, "sensor/+", "sensor/data", "#");
            pub.publishQos0("sensor/data", utf8("one"), false);
            assertEquals("one", new String(sub.readPublish().payload(), StandardCharsets.UTF_8));
            sub.assertNothingPendingBeforePing();
        }
    }

    @Test
    void resubscribingSameFilterDoesNotDuplicateDelivery() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var sub = RawClient.connected(running.port(), "sub", 60)) {
            sub.subscribe(1, "a/b");
            sub.subscribe(2, "a/b");
            pub.publishQos0("a/b", utf8("x"), false);
            sub.readPublish();
            sub.assertNothingPendingBeforePing();
        }
    }

    @Test
    void sysTopicsAreNotMatchedByLeadingWildcards() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var wild = RawClient.connected(running.port(), "wild", 60);
             var sys = RawClient.connected(running.port(), "sys", 60)) {
            wild.subscribe(1, "#", "+/x");
            sys.subscribe(1, "$SYS/#");
            pub.publishQos0("$SYS/x", utf8("v"), true);
            assertEquals("$SYS/x", sys.readPublish().topic());
            wild.assertNothingPendingBeforePing();

            // Retained $SYS message is not replayed to a '#' subscriber either.
            try (var late = RawClient.connected(running.port(), "late", 60)) {
                late.subscribe(1, "#");
                late.assertNothingPendingBeforePing();
            }
        }
    }

    @Test
    void unsubscribingOneFilterKeepsTheOther() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60);
             var sub = RawClient.connected(running.port(), "sub", 60)) {
            sub.subscribe(1, "a/+", "b/+");
            sub.unsubscribe(2, "a/+");
            pub.publishQos0("a/1", utf8("gone"), false);
            pub.publishQos0("b/1", utf8("kept"), false);
            Publish got = sub.readPublish();
            assertEquals("b/1", got.topic());
            assertEquals("kept", new String(got.payload(), StandardCharsets.UTF_8));
            sub.assertNothingPendingBeforePing();
        }
    }

    @Test
    void disconnectedClientIsRemovedFromRouter() throws Exception {
        try (var running = RunningBroker.open();
             var pub = RawClient.connected(running.port(), "pub", 60)) {
            var sub = RawClient.connected(running.port(), "sub", 60);
            sub.subscribe(1, "a/#", "b");
            assertEquals(1, running.server().router().subscriberCount());
            sub.close(); // abrupt: no DISCONNECT
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (running.server().router().subscriberCount() != 0
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(0, running.server().router().subscriberCount());
            // Publishing afterwards must not fail the publisher.
            pub.publishQos1("a/x", utf8("x"), false, 1);
        }
    }

    // ---------------------------------------------------------------- Will

    @Test
    void publishesRetainedWillAfterAbruptDisconnect() throws Exception {
        try (var running = RunningBroker.open();
             var observer = new MqttClient(running.uri(), "observer", new MemoryPersistence());
             var edge = new MqttClient(running.uri(), "edge", new MemoryPersistence())) {
            var statuses = new LinkedBlockingQueue<String>();
            var options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName("user1");
            options.setPassword("123456".toCharArray());
            observer.connect(options);
            observer.subscribe("sensor/status", 1, (topic, msg) ->
                statuses.add(new String(msg.getPayload(), StandardCharsets.UTF_8)));
            options.setWill("sensor/status", "OFFLINE".getBytes(StandardCharsets.UTF_8), 1, true);
            edge.connect(options);
            edge.disconnectForcibly(0, 0, false);
            assertEquals("OFFLINE", statuses.poll(3, TimeUnit.SECONDS));
            assertNull(statuses.poll(300, TimeUnit.MILLISECONDS)); // exactly once

            // The retained Will is replayed to a late subscriber.
            try (var late = RawClient.connected(running.port(), "late", 60)) {
                late.subscribe(1, "sensor/status");
                Publish replay = late.readPublish();
                assertEquals("OFFLINE", new String(replay.payload(), StandardCharsets.UTF_8));
                assertTrue(replay.retain());
            }
            observer.disconnect();
        }
    }

    @Test
    void gracefulDisconnectDoesNotPublishWill() throws Exception {
        try (var running = RunningBroker.open();
             var observer = RawClient.connected(running.port(), "observer", 60);
             var edge = new RawClient(running.port())) {
            observer.subscribe(1, "sensor/status");
            assertEquals(0, edge.connectAndGetCode(RawClient.connectWithWill(
                "edge", 60, "sensor/status", utf8("OFFLINE"), 0, false)));
            edge.send(new Packet(MqttCodec.DISCONNECT, 0, new byte[0]));
            edge.assertClosed();
            observer.assertNothingPendingBeforePing();
            // Give a (wrongly) published Will time to arrive, then re-check.
            Thread.sleep(200);
            observer.assertNothingPendingBeforePing();
        }
    }

    @Test
    void failedAuthenticationDoesNotPublishWill() throws Exception {
        try (var running = RunningBroker.open();
             var observer = RawClient.connected(running.port(), "observer", 60);
             var edge = paho(running, "edge")) {
            observer.subscribe(1, "sensor/status");
            var bad = options();
            bad.setPassword("wrong".toCharArray());
            bad.setWill("sensor/status", utf8("OFFLINE"), 0, false);
            assertThrows(MqttException.class, () -> edge.connect(bad));
            Thread.sleep(200);
            observer.assertNothingPendingBeforePing();
        }
    }

    @Test
    void duplicateClientIdPublishesOldSessionsWillOnce() throws Exception {
        try (var running = RunningBroker.open();
             var observer = RawClient.connected(running.port(), "observer", 60);
             var first = new RawClient(running.port());
             var second = new RawClient(running.port())) {
            observer.subscribe(1, "sensor/status");
            assertEquals(0, first.connectAndGetCode(RawClient.connectWithWill(
                "dup", 60, "sensor/status", utf8("OFFLINE-1"), 0, false)));
            assertEquals(0, second.connectAndGetCode(RawClient.connectWithWill(
                "dup", 60, "sensor/status", utf8("OFFLINE-2"), 0, false)));
            first.assertClosed();
            Publish will = observer.readPublish();
            assertEquals("OFFLINE-1", new String(will.payload(), StandardCharsets.UTF_8));
            Thread.sleep(200);
            observer.assertNothingPendingBeforePing(); // no second copy, no OFFLINE-2 yet

            // The replacement's own Will still fires when it dies abruptly.
            second.close();
            assertEquals("OFFLINE-2",
                new String(observer.readPublish().payload(), StandardCharsets.UTF_8));
        }
    }
}
