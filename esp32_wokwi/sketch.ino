#include <WiFi.h>
#include <PubSubClient.h>
#include <DHTesp.h>
#include <ArduinoJson.h>

#define DHTPIN 16
#define LEDPIN 4

const char* ssid = "Wokwi-GUEST";
const char* password = "";

const char* mqtt_server = "broker.hivemq.com";
const int mqtt_port = 1883;
const char* mqtt_user = "user1";
const char* mqtt_pass = "123456";

const char* topic_data = "thuantran260/sensor/data";
const char* topic_led = "thuantran260/sensor/led";
const char* topic_status = "thuantran260/sensor/status";

WiFiClient espClient;
PubSubClient client(espClient);
DHTesp dht;

unsigned long lastMsg = 0;
const unsigned long interval = 3000;

void callback(char* topic, byte* payload, unsigned int length) {
  String msg = "";
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }
  msg.trim();

  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("]: ");
  Serial.println(msg);

  if (String(topic) == topic_led || String(topic) == "sensor/led") {
    if (msg.equalsIgnoreCase("ON") || msg == "1") {
      digitalWrite(LEDPIN, HIGH);
      Serial.println("LED turned ON (HIGH)");
    } else if (msg.equalsIgnoreCase("OFF") || msg == "0") {
      digitalWrite(LEDPIN, LOW);
      Serial.println("LED turned OFF (LOW)");
    } else {
      Serial.print("Unrecognized command: ");
      Serial.println(msg);
    }
  }
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection to ");
    Serial.print(mqtt_server);
    Serial.print("...");

    String clientId = "ESP32Client-" + String(random(0xffff), HEX);

    if (client.connect(clientId.c_str(), mqtt_user, mqtt_pass, topic_status, 1, true, "OFFLINE")) {
      Serial.println("connected to HiveMQ!");
      client.publish(topic_status, "ONLINE", true);
      client.publish("sensor/status", "ONLINE", true);
      client.subscribe(topic_led);
      client.subscribe("sensor/led");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" retrying in 5 seconds...");
      delay(5000);
    }
  }
}

void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
}

void setup() {
  Serial.begin(115200);

  pinMode(LEDPIN, OUTPUT);
  digitalWrite(LEDPIN, LOW);

  dht.setup(DHTPIN, DHTesp::DHT22);

  setup_wifi();

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
  client.setBufferSize(256);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    setup_wifi();
  }

  if (!client.connected()) {
    reconnect();
  }

  client.loop();

  unsigned long now = millis();
  if (now - lastMsg >= interval) {
    lastMsg = now;

    TempAndHumidity data = dht.getTempAndHumidity();

    if (isnan(data.temperature) || isnan(data.humidity)) {
      Serial.println("Failed to read from DHT22 sensor! Skipping publish.");
      return;
    }

    StaticJsonDocument<128> doc;
    doc["temp"] = serialized(String(data.temperature, 1));
    doc["hum"] = serialized(String(data.humidity, 1));

    char buffer[128];
    serializeJson(doc, buffer);

    Serial.print("Publishing to sensor/data: ");
    Serial.println(buffer);

    client.publish(topic_data, buffer);
    client.publish("sensor/data", buffer);
    client.publish(topic_status, "ONLINE", true);
    client.publish("sensor/status", "ONLINE", true);
  }
}
