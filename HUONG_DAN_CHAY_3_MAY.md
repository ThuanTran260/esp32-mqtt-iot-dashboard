# Hướng Dẫn Phân Chia & Triển Khai Hệ Thống IoT Trên 3 Máy Tính Riêng Biệt

Tài liệu này hướng dẫn cách phân bổ và triển khai hệ thống **ESP32 - Mosquitto Broker - Web Dashboard** trên 3 máy tính riêng lẻ kết nối qua cùng một mạng LAN (WiFi lớp học / Router / Hotspot di động).

---

## 1. Phân Chia Vai Trò Hợp Lý Nhất Cho 3 Máy

| Máy | Vai trò đề xuất | Lý do & Khối lượng cài đặt |
|---|---|---|
| **Máy Hiện Tại** | **Máy ESP32 (Wokwi Simulation)** | **Tối ưu nhất:** Máy này **đã cài đặt sẵn PlatformIO Core**, **đã biên dịch hoàn tất firmware** ESP32 (`firmware.bin` và `firmware.elf`), cấu hình Wokwi đã chuẩn 100%. Nếu chuyển phần này sang máy khác sẽ phải tốn thời gian tải toolchain C++ rất nặng. |
| **Máy Thứ 2** | **Máy chủ MQTT Broker (Mosquitto)** | **Siêu nhẹ:** Chỉ cần cài đặt phần mềm Eclipse Mosquitto (~2 MB), mở tường lửa cho cổng 1883 & 9001 và chạy file cấu hình có sẵn. |
| **Máy Thứ 3** | **Máy Web Dashboard (Frontend)** | **Đơn giản nhất:** Chỉ cần trình duyệt web (Chrome/Edge) và mở file HTML/JS, không cần cài đặt môi trường lập trình nhúng. |

---

## 2. Kiến Trúc Mạng Giữa 3 Máy

```
  [ MÁY 1 (MÁY HIỆN TẠI) ]                      [ MÁY 3 (WEB DASHBOARD) ]
    ESP32 Wokwi Simulation                        Giao diện Web hiển thị
       (IP: 192.168.1.X)                             (IP: 192.168.1.Z)
              │                                              │
              │ TCP: 1883                                    │ WebSocket: 9001
              └──────────────────────┐    ┌──────────────────┘
                                     ▼    ▼
                              [ MÁY 2 (BROKER) ]
                            Mosquitto MQTT Server
                              (IP: 192.168.1.50)
```

> **Điều kiện bắt buộc:** Cả 3 máy phải kết nối vào **cùng 1 mạng WiFi** (hoặc 1 máy bật Mobile Hotspot phát WiFi cho 2 máy còn lại kết nối vào).

---

## 3. Hướng Dẫn Chi Tiết Cho Từng Máy

---

### MÁY 2: Máy Chủ MQTT Broker (Mosquitto)

Máy này đóng vai trò máy chủ trung tâm nhận và phân phối toàn bộ gói tin MQTT.

#### Bước 1: Cài đặt Mosquitto
* Mở **PowerShell (Run as Administrator)** và gõ:
  ```powershell
  winget install EclipseFoundation.Mosquitto
  ```
  *(Hoặc tải bộ cài Windows `.exe` từ trang chủ: [https://mosquitto.org/download/](https://mosquitto.org/download/))*.

#### Bước 2: Mở Tường Lửa (Windows Firewall)
> **CỰC KỲ QUAN TRỌNG:** Mặc định Windows sẽ chặn máy khác truy cập vào cổng 1883 và 9001.
* Chạy lệnh sau trong PowerShell (Admin) trên Máy 2:
  ```powershell
  New-NetFirewallRule -DisplayName "Mosquitto Broker" -Direction Inbound -LocalPort 1883,9001 -Protocol TCP -Action Allow
  ```

#### Bước 3: Lấy địa chỉ IP mạng LAN của Máy 2
* Mở CMD/PowerShell gõ:
  ```cmd
  ipconfig
  ```
* Tìm dòng **IPv4 Address** (Ví dụ: `192.168.1.50`). Hãy ghi lại địa chỉ IP này để cung cấp cho Máy 1 và Máy 3.

#### Bước 4: Sao chép thư mục và Khởi chạy Broker
* Sao chép toàn bộ thư mục `broker/` từ dự án sang Máy 2 (gồm: `mosquitto.conf`, `password.txt`, `start_broker.bat`).
* Nhấp đúp chạy file:
  ```cmd
  start_broker.bat
  ```
* Màn hình hiện thông báo lắng nghe trên cổng `1883` và `9001` là hoàn tất.

---

### MÁY 3: Máy Khách Web Dashboard

Máy này chỉ dùng để hiển thị đồng hồ đo và điều khiển bật/tắt đèn LED.

#### Bước 1: Sao chép thư mục Web
* Sao chép toàn bộ thư mục `web_dashboard/` sang Máy 3.

#### Bước 2: Cài đặt phần mềm cần thiết
* Chỉ cần có trình duyệt web (Google Chrome, Microsoft Edge, Firefox).
* *(Tùy chọn tiện lợi)* Cài đặt **VS Code** kèm extension **Live Server**, hoặc máy có sẵn **Python**.

#### Bước 3: Chỉnh sửa địa chỉ kết nối Broker
* Mở file `web_dashboard/app.js` trên Máy 3.
* Tìm đến dòng **33 - 35** và sửa lại như sau:
  ```javascript
  const USE_PUBLIC_BROKER = false; // Chuyển sang dùng Mosquitto của Máy 2
  const BROKER_URL = "ws://192.168.1.50:9001"; // <<< THAY BẰNG IP CỦA MÁY 2
  const TOPIC_PREFIX = ""; // Để rỗng để dùng topic chuẩn
  ```

#### Bước 4: Khởi chạy Web
* Dùng Live Server mở file `web_dashboard/index.html`.
* Hoặc mở CMD tại thư mục `web_dashboard` chạy lệnh:
  ```cmd
  python -m http.server 8000
  ```
  Sau đó vào trình duyệt gõ: `http://localhost:8000`.
* **Dấu hiệu thành công:** Huy hiệu góc trên chuyển sang màu xanh lá **Connected**.

---

### MÁY 1 (MÁY HIỆN TẠI): Mô Phỏng ESP32 Wokwi

Máy này đại diện cho thiết bị vi điều khiển ở biên (Edge IoT Device).

#### Bước 1: Trỏ code ESP32 sang Broker của Máy 2
* Mở file `esp32_wokwi/sketch.ino`.
* Sửa lại địa chỉ MQTT Server (dòng 12-15) sang IP của Máy 2:
  ```cpp
  const char* mqtt_server = "192.168.1.50"; // <<< THAY BẰNG IP CỦA MÁY 2
  const int mqtt_port = 1883;
  const char* mqtt_user = "user1";
  const char* mqtt_pass = "123456";

  const char* topic_data = "sensor/data";
  const char* topic_led = "sensor/led";
  const char* topic_status = "sensor/status";
  ```

#### Bước 2: Biên dịch lại Firmware
* Mở Terminal tại thư mục dự án và chạy:
  ```powershell
  python -m platformio run
  ```
  *(PlatformIO sẽ cập nhật file firmware binary mới tại `.pio/build/esp32doit-devkit-v1/firmware.bin`)*.

#### Bước 3: Khởi chạy mô phỏng Wokwi
* Mở file `diagram.json` trong VS Code > Nhấn **Play** (hoặc `F1` > `Wokwi: Start Simulator`).
* **Lưu ý mạng của Wokwi:** Vì Wokwi chạy trong sandbox, để nó kết nối được tới IP nội bộ `192.168.1.50` của Máy 2, bạn cần chạy thêm công cụ **Wokwi IoT Gateway** ([wokwi-bridge](https://github.com/wokwi/wokwi-features/issues/100)) trên máy này.

---

## 4. Cách Kiểm Tra Kết Nối Mạng Giữa Các Máy (Troubleshooting)

Trước khi khởi động toàn bộ, hãy đứng từ **Máy 1** và **Máy 3** mở PowerShell gõ lệnh kiểm tra xem có "thông đường" tới **Máy 2 (Broker)** không:

```powershell
# 1. Kiểm tra Ping tới Máy 2
ping 192.168.1.50

# 2. Kiểm tra cổng TCP 1883 của Broker
Test-NetConnection 192.168.1.50 -Port 1883

# 3. Kiểm tra cổng WebSocket 9001 của Broker
Test-NetConnection 192.168.1.50 -Port 9001
```

* Nếu `TcpTestSucceeded : True` ➡️ Đã kết nối thông mạng thành công 100%!
* Nếu `TcpTestSucceeded : False` ➡️ Hãy kiểm tra lại:
  1. Máy 2 đã tắt/mở rule tường lửa chưa?
  2. Cả 3 máy có đang kết nối chung 1 mạng WiFi không?
  3. Mạng WiFi ở trường có bật tính năng "Client Isolation" (chặn các máy trong cùng mạng thấy nhau) không? (Nếu có, giải pháp là 1 máy bật Hotspot cho 2 máy kia kết nối).

---

## 5. Phương Án Dự Phòng Siêu Tốc (Dùng Cloud Broker HiveMQ)

Nếu mạng WiFi trường học chặn kết nối mạng ngang hàng (LAN) giữa các máy:
* Không cần cài đặt Mosquitto ở Máy 2.
* Cả 3 máy chỉ cần có internet và giữ nguyên cấu hình mặc định ban đầu:
  * Broker: `broker.hivemq.com` (Cổng TCP `1883` cho ESP32, cổng WSS `8884` cho Web Dashboard).
  * Topic: `thuantran260/sensor/#`.
* Cả 3 máy dù ở 3 mạng WiFi khác nhau hay dùng 4G đều kết nối được với nhau ngay lập tức!
