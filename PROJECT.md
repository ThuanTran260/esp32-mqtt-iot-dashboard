# Project: ESP32 (Wokwi) + Broker MQTT Java tự viết + CLI Java

## Kiến trúc

```
 ESP32 (Wokwi, Arduino/C++, PubSubClient)
     |  MQTT 3.1.1 trên TCP 1883, xác thực user1/123456
     v
 Broker Java (java-broker)  <-- TCP 1883 --  CLI Java (java-desktop, Eclipse Paho 1.2.5)
```

- Broker viết bằng Java chuẩn (Java 21+), không nhúng broker có sẵn. Paho chỉ dùng cho client Java và test.
- Mặc định broker bind `0.0.0.0:1883`; client và firmware nhập IPv4 thật của máy broker, không nhập `0.0.0.0`.
- Firmware đọc địa chỉ broker từ `esp32_wokwi/broker_config.h` (macro `MQTT_BROKER_HOST`, `MQTT_BROKER_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD`).

## Hợp đồng topic và payload

| Topic | Hướng | Payload |
|---|---|---|
| `sensor/data` | ESP32 -> client | `{"temp":28.5,"hum":65}` (số thật làm tròn 1 chữ số thập phân, chu kỳ 3000 ms) |
| `sensor/led` | client -> ESP32 | `ON` / `OFF` (GPIO4) |
| `sensor/status` | ESP32 -> client | `ONLINE` / `OFFLINE`, retained (`OFFLINE` là Will) |

Phần cứng: DHT22 ở GPIO16, LED ở GPIO4. ESP32 dùng keepalive 15 s.

## Vai trò máy

- **Chế độ 1 máy:** máy Wokwi chạy cả broker, simulator và CLI (firmware dùng `host.wokwi.internal`).
- **Chế độ 3 máy:** máy broker (broker Java + firewall chỉ cho subnet hotspot), máy Wokwi (PlatformIO + Wokwi), máy dashboard/client (CLI Java). Chi tiết ở `docs/java-runbook.md`.

## Mã nguồn

```
java-broker/      Broker MQTT (protocol/, routing/, server/), JAR chạy được
java-desktop/     CLI client (check/subscribe/publish) trên Paho
esp32_wokwi/      sketch.ino, diagram.json, libraries.txt, broker_config.example.h
scripts/          start_java_broker.bat, check_java_connection.bat, subscribe_java.bat, publish_led.bat
config/           broker.example.properties, client.example.properties
tools/spike/      TcpProbe.java (dò cổng TCP)
docs/             java-runbook.md, validation/
platformio.ini    env esp32doit-devkit-v1
wokwi.toml        trỏ tới firmware build bằng PlatformIO
```

File cục bộ không commit: `config/broker.properties`, `config/client.properties`, `esp32_wokwi/broker_config.h`, `**/target/`.

## Trạng thái

- Broker Java, CLI Java, firmware đọc `broker_config.h`: đã có, kiểm thử bằng Maven (`mvn clean verify`).
- Kiểm tra 1 máy phần mềm: `docs/validation/single-machine-results.md`. Các bước cần Wokwi do người bấm Start Simulator giữ `NOT RUN` đến khi có người chạy.
- Chưa nghiệm thu 3 máy thật; chỉ ghi "3 máy pass" khi có bằng chứng từ ba máy.

## Giới hạn MVP

- Không có QoS 1 chiều từ broker xuống client (mọi grant là QoS 0).
- Không có TLS, không có WebSocket.
- Tin retained mất khi khởi động lại broker.
- Chưa có Swing dashboard (dự kiến ở các task B2/B3 của plan `2026-11-14-java-desktop-hotspot.md`).

## Việc tiếp theo

1. Swing dashboard (model + UI), bật lại mode `dashboard` của CLI.
2. Nghiệm thu 3 máy theo runbook phần 3, lưu `docs/validation/YYYY-MM-DD-three-machine-results.md` với ngày chạy thật.
3. Các lỗi nhỏ đã hoãn (xem plan).
