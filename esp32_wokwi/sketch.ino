#include <WiFi.h>
#include <PubSubClient.h>
#include <DHTesp.h>
#include <ArduinoJson.h>

#define DHTPIN 16
#define LEDPIN 4

const char* ssid = "Wokwi-GUEST";
const char* password = "";

#if __has_include("broker_config.h")
#include "broker_config.h"
#else
#error "Copy esp32_wokwi/broker_config.example.h to esp32_wokwi/broker_config.h and set MQTT_BROKER_HOST"
#endif

const char* mqtt_server = MQTT_BROKER_HOST;
const int mqtt_port = MQTT_BROKER_PORT;
const char* mqtt_user = MQTT_USERNAME;
const char* mqtt_pass = MQTT_PASSWORD;

const char* topic_data = "sensor/data";
const char* topic_led = "sensor/led";
const char* topic_status = "sensor/status";

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

  if (String(topic) == topic_led) {
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
      Serial.println("connected to Java MQTT broker");
      client.publish(topic_status, "ONLINE", true);
      client.subscribe(topic_led);
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
  client.setKeepAlive(15);
  client.setSocketTimeout(5);
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
    // Round in double: a float would serialize as 28.10000038 with ArduinoJson 6.
    doc["temp"] = round(data.temperature * 10.0) / 10.0;
    doc["hum"] = round(data.humidity * 10.0) / 10.0;

    char buffer[128];
    serializeJson(doc, buffer);

    Serial.print("Publishing to sensor/data: ");
    Serial.println(buffer);

    if (!client.publish(topic_data, buffer)) {
      Serial.println("Publish to sensor/data failed");
    }
    if (!client.publish(topic_status, "ONLINE", true)) {
      Serial.println("Publish to sensor/status failed");
    }
  }
}
