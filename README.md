# Hệ thống IoT: ESP32 (Wokwi) - Broker MQTT Java tự viết - CLI Java

Bài thực hành môn Lập trình mạng (SGU). ESP32 mô phỏng trên Wokwi đọc DHT22 và điều khiển LED, giao tiếp qua **broker MQTT tự viết bằng Java** (không dùng Mosquitto, HiveMQ hay Moquette). Client phía PC là CLI Java dùng Eclipse Paho.

Hướng dẫn chạy chi tiết (1 máy và 3 máy, xử lý lỗi): **[docs/java-runbook.md](docs/java-runbook.md)**.

## Kiến trúc

```
 ESP32 (Wokwi, Arduino + PubSubClient)
        |  TCP 1883, user1/123456
        v
 Broker Java tự viết  (java-broker, bind 0.0.0.0:1883)
        ^
        |  TCP 1883
 CLI Java (java-desktop, Paho): check / subscribe / publish
```

- `java-broker/`: broker MQTT (bản tối thiểu: CONNECT có xác thực, SUBSCRIBE với wildcard, PUBLISH, retained, Will, keepalive).
- `java-desktop/`: client CLI (`check`, `subscribe`, `publish`).
- `esp32_wokwi/`: firmware Arduino (`sketch.ino`), sơ đồ mạch (`diagram.json`), cấu hình broker (`broker_config.example.h`).
- `scripts/`: launcher `.bat` cho broker và CLI.
- `config/`: file cấu hình mẫu `*.example.properties`.
- `tools/spike/TcpProbe.java`: công cụ dò cổng TCP khi gỡ lỗi mạng.

Phần cứng giả lập: DHT22 ở GPIO16, LED ở GPIO4 (điện trở 220 Ohm), Wi-Fi `Wokwi-GUEST`.

## Topic và payload

| Topic | Hướng | Payload | Ghi chú |
|---|---|---|---|
| `sensor/data` | ESP32 -> broker -> client | `{"temp":28.5,"hum":65.0}` | Số thật, gửi mỗi 3000 ms |
| `sensor/led` | client -> broker -> ESP32 | `ON` / `OFF` | Điều khiển LED GPIO4 |
| `sensor/status` | ESP32 -> broker -> client | `ONLINE` / `OFFLINE` | Retained; `OFFLINE` là Will |

Tài khoản demo `user1` / `123456` (cấu hình được).

## Bắt đầu nhanh (chế độ 1 máy)

```powershell
mvn clean verify
Copy-Item config\client.example.properties config\client.properties   # sửa host= thành IPv4 Wi-Fi của máy này
Copy-Item esp32_wokwi\broker_config.example.h esp32_wokwi\broker_config.h
scripts\start_java_broker.bat          # terminal 1
pio run -e esp32doit-devkit-v1         # build firmware; sau đó F1 -> Wokwi: Start Simulator
scripts\subscribe_java.bat             # terminal 2
scripts\publish_led.bat ON             # terminal 3 (hoặc OFF)
```

Chạy 3 máy: xem [HUONG_DAN_CHAY_3_MAY.md](HUONG_DAN_CHAY_3_MAY.md) và runbook.

## Yêu cầu

- JDK 21 trở lên, Maven (mọi máy chạy broker/client).
- PlatformIO và VS Code + extension Wokwi (chỉ máy Wokwi).

## File cấu hình cục bộ (không commit)

`config/broker.properties`, `config/client.properties`, `esp32_wokwi/broker_config.h`, `**/target/`. Tạo từ các file `*.example` tương ứng.

## Giới hạn của MVP

- Không có QoS 1 chiều từ broker xuống client: mọi subscription được cấp QoS 0.
- Không có TLS, không có WebSocket.
- Tin retained mất khi khởi động lại broker.
- Chưa có Swing dashboard; hiện dùng CLI Java.
- Chưa có bằng chứng chạy 3 máy thật. Kết quả kiểm tra 1 máy: `docs/validation/single-machine-results.md`.
