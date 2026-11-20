# Hướng dẫn chạy hệ thống trên 3 máy (broker Java)

Tài liệu ngắn tóm tắt vai trò ba máy. Các lệnh đầy đủ, kiểm tra từng bước và bảng xử lý lỗi nằm ở **[docs/java-runbook.md](docs/java-runbook.md)** (phần 3 và phần 4); hãy làm theo runbook khi chạy thật.

## 1. Vai trò ba máy

| Vai trò | Cần cài | Việc làm |
|---|---|---|
| **Máy broker** | JDK 21+, Maven | Chạy `scripts\start_java_broker.bat`, mở firewall TCP 1883 chỉ cho subnet hotspot |
| **Máy Wokwi** | VS Code + Wokwi, PlatformIO (đã có sẵn toolchain, nên giữ nguyên vai trò) | Đặt `MQTT_BROKER_HOST` = IPv4 máy broker, build lại firmware, Start Simulator |
| **Máy dashboard/client** | JDK 21+ (và JAR đã build) | `check_java_connection.bat`, `subscribe_java.bat`, `publish_led.bat ON/OFF` |

Vai trò gọi theo tên, không theo số thứ tự máy, để tránh nhập nhầm IP.

```
  Máy Wokwi (ESP32)               Máy dashboard/client (CLI)
        \                               /
         \ TCP 1883                    / TCP 1883
          v                           v
                 Máy broker (Java, 0.0.0.0:1883)
```

Cả ba máy phải cùng mạng (hotspot) và cùng một commit. Cùng SSID chưa chắc liên lạc được vì hotspot có thể bật client isolation.

## 2. Thứ tự thao tác

1. **Máy broker:** `git pull --ff-only`, `mvn clean verify`, `ipconfig` (ghi IPv4 hotspot, bỏ qua adapter ảo), `scripts\start_java_broker.bat`, rồi chạy lệnh `New-NetFirewallRule ... -RemoteAddress <subnet>` trong runbook mục 3.1 (PowerShell Administrator).
2. **Máy Wokwi và máy dashboard:** `Test-NetConnection <IP broker> -Port 1883` phải trả `TcpTestSucceeded : True`.
3. **Máy Wokwi:** sửa `esp32_wokwi/broker_config.h` (`MQTT_BROKER_HOST "<IP broker>"`), `pio run -e esp32doit-devkit-v1`, `F1` -> Wokwi: Start Simulator. Serial phải hiện `connected to Java MQTT broker`.
4. **Máy dashboard/client:** tạo `config\client.properties` từ file mẫu, đặt `host=<IP broker>`, chạy `scripts\check_java_connection.bat`, `scripts\subscribe_java.bat`, và `scripts\publish_led.bat ON` / `OFF`.

## 3. Nếu Wokwi không ra được LAN

Nếu ESP32 log `failed, rc=-2` liên tục trong khi `Test-NetConnection` trên máy Wokwi là True: ghi lại hiện tượng, rồi chuyển broker sang máy Wokwi (dùng `host.wokwi.internal`), máy còn lại làm client. Có thể dùng `java tools/spike/TcpProbe.java 1883` để tách lỗi mạng khỏi lỗi broker (runbook mục 3.6).

## 4. Ghi chú

- Máy dashboard có thể dùng Swing dashboard (`java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar dashboard`) hoặc CLI Java. Layout cửa sổ chưa kiểm tra trên màn hình thật.
- Giới hạn MVP: không QoS 1 chiều xuống client, không TLS/WebSocket, retained mất khi restart broker.
- Chỉ ghi "3 máy pass" khi có bằng chứng từ ba máy thật (`docs/validation/YYYY-MM-DD-three-machine-results.md`).
