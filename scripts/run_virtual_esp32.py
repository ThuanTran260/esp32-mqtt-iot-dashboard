import time
import json
import random
import paho.mqtt.client as mqtt

BROKER = "broker.hivemq.com"
PORT = 1883
TOPIC_DATA = "thuantran260/sensor/data"
TOPIC_LED = "thuantran260/sensor/led"
TOPIC_STATUS = "thuantran260/sensor/status"

led_state = "OFF"

def on_connect(client, userdata, flags, rc, properties=None):
    print(f"[Virtual ESP32] Connected to broker {BROKER}:{PORT} with code {rc}")
    client.publish(TOPIC_STATUS, "ONLINE", retain=True)
    client.subscribe(TOPIC_LED)
    print(f"[Virtual ESP32] Subscribed to {TOPIC_LED} and published ONLINE status")

def on_message(client, userdata, msg):
    global led_state
    cmd = msg.payload.decode().strip()
    print(f"[Virtual ESP32] Received command on {msg.topic}: {cmd}")
    if cmd.upper() in ["ON", "1"]:
        led_state = "ON"
        print("[Virtual ESP32] -> Physical LED on GPIO 4 is now ON")
    elif cmd.upper() in ["OFF", "0"]:
        led_state = "OFF"
        print("[Virtual ESP32] -> Physical LED on GPIO 4 is now OFF")

def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.will_set(TOPIC_STATUS, "OFFLINE", retain=True)
    client.on_connect = on_connect
    client.on_message = on_message

    print("[Virtual ESP32] Connecting to broker...")
    client.connect(BROKER, PORT, 60)
    client.loop_start()

    base_temp = 28.0
    base_hum = 65.0

    try:
        while True:
            # Simulate slight sensor fluctuations
            temp = round(base_temp + random.uniform(-1.5, 1.5), 1)
            hum = round(base_hum + random.uniform(-3.0, 3.0), 1)
            payload = json.dumps({"temp": temp, "hum": hum})
            
            client.publish(TOPIC_DATA, payload)
            print(f"[Virtual ESP32] Telemetry published: {payload} | LED: {led_state}")
            time.sleep(3)
    except KeyboardInterrupt:
        print("[Virtual ESP32] Stopping...")
    finally:
        client.publish(TOPIC_STATUS, "OFFLINE", retain=True)
        client.loop_stop()
        client.disconnect()
        print("[Virtual ESP32] Disconnected.")

if __name__ == "__main__":
    main()
