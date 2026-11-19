# Runbook: demo ESP32 - broker Java - CLI Java

Tài liệu này hướng dẫn chạy hệ thống theo hai chế độ: **1 máy** (mọi thứ trên máy Wokwi) và **3 máy** (broker, Wokwi, dashboard/client tách riêng). Mọi lệnh chạy từ thư mục gốc của repo.

Luồng dữ liệu: `ESP32 (Wokwi, PubSubClient)` ⇄ `broker Java tự viết (TCP 1883)` ⇄ `CLI Java (Paho)`.

| Topic | Payload | Ghi chú |
|---|---|---|
| `sensor/data` | `{"temp":28.5,"hum":65}` | ESP32 gửi mỗi 3000 ms, số thật (không phải chuỗi), làm tròn 1 chữ số thập phân; số nguyên in không có `.0` |
| `sensor/led` | `ON` / `OFF` | Client gửi, ESP32 nhận, điều khiển LED GPIO4 |
| `sensor/status` | `ONLINE` / `OFFLINE` | Retained; `OFFLINE` là Will của ESP32 |

Tài khoản demo: `user1` / `123456` (đổi được trong `config/broker.properties`, `config/client.properties` và `esp32_wokwi/broker_config.h`; ba nơi phải khớp nhau).

---

## 1. Chuẩn bị chung

### 1.1. Công cụ cần cài

- **JDK 21 trở lên** (`java -version`) và **Maven** (`mvn -v`) trên mọi máy chạy broker hoặc client. Có thể build một lần rồi copy hai file JAR sang máy khác thay vì cài Maven (cùng một commit).
- **PlatformIO + VS Code + extension Wokwi** chỉ cần trên **máy Wokwi** (build firmware và chạy simulator).

### 1.2. Build và kiểm tra phần mềm

```powershell
mvn clean verify
```

Kết quả mong đợi: `BUILD SUCCESS`, tạo `java-broker/target/java-broker-1.0-SNAPSHOT.jar` và `java-desktop/target/java-desktop-1.0-SNAPSHOT.jar`. Các script `scripts\*.bat` đều cần hai JAR này.

### 1.3. Xem IPv4 của máy

```powershell
ipconfig
```

Chọn adapter mạng thật (**Wi-Fi hoặc Ethernet**, hoặc hotspot) đang kết nối mạng demo, lấy dòng `IPv4 Address`. **Bỏ qua** các adapter ảo: `vEthernet (...)`, VPN, VirtualBox / VMware, địa chỉ `169.254.x.x`. Khi broker khởi động, nó cũng in danh sách IPv4 của máy để đối chiếu.

Không bao giờ nhập `0.0.0.0` vào cấu hình client hoặc firmware. `0.0.0.0` chỉ là địa chỉ bind của broker (lắng nghe mọi adapter).

### 1.4. File cấu hình cục bộ (không commit)

| File mẫu | File dùng thật (gitignored) | Ai tạo |
|---|---|---|
| `config/broker.example.properties` | `config/broker.properties` | `start_java_broker.bat` tự copy nếu chưa có |
| `config/client.example.properties` | `config/client.properties` | Bạn copy thủ công |
| `esp32_wokwi/broker_config.example.h` | `esp32_wokwi/broker_config.h` | Bạn copy thủ công |

```powershell
Copy-Item config\client.example.properties config\client.properties
Copy-Item esp32_wokwi\broker_config.example.h esp32_wokwi\broker_config.h
```

Nếu file đã tồn tại thì không copy đè; hãy sửa trực tiếp file đó.

---

## 2. Chế độ 1 máy

Tất cả chạy trên **máy Wokwi**: broker, Wokwi simulator và CLI.

1. **Terminal 1, chạy broker:**

   ```powershell
   scripts\start_java_broker.bat
   ```

   Mong đợi dòng `Java MQTT broker listening on 0.0.0.0:1883` và danh sách IPv4 của máy. Để terminal này mở.

2. **Firmware:** mở `esp32_wokwi/broker_config.h` và để `MQTT_BROKER_HOST` là `"host.wokwi.internal"` (giá trị mặc định của file mẫu). Build trong terminal PlatformIO của VS Code:

   ```powershell
   pio run -e esp32doit-devkit-v1
   ```

   Nếu `pio` không có trong PATH, dùng đường dẫn đầy đủ, ví dụ `C:/Users/<tên bạn>/.platformio/penv/Scripts/pio.exe run -e esp32doit-devkit-v1`.

3. **Wokwi:** trong VS Code nhấn `F1` rồi chọn **Wokwi: Start Simulator** (workspace root có `wokwi.toml` trỏ tới `.pio/build/esp32doit-devkit-v1/firmware.bin`). Serial monitor phải hiện `connected to Java MQTT broker`.

4. **Client:** sửa `config/client.properties`, đặt `host=<IPv4 adapter mạng thật của máy này (Wi-Fi hoặc Ethernet)>` (lấy ở mục 1.3). Không dùng `0.0.0.0`.

5. **Terminal 2, xem dữ liệu:**

   ```powershell
   scripts\subscribe_java.bat
   ```

   Mỗi dòng có dạng `HH:mm:ss <topic> <payload>`. Mong đợi thấy `sensor/status ONLINE` (retained) và một dòng `sensor/data {...}` khoảng mỗi 3 giây. Script không nhận thêm tham số (truyền thêm đối số sẽ báo `Duplicate option`).

6. **Terminal 3, điều khiển LED:**

   ```powershell
   scripts\publish_led.bat ON
   scripts\publish_led.bat OFF
   ```

   LED trong Wokwi sáng / tắt; terminal 2 thấy dòng có `sensor/led ON` / `sensor/led OFF`.

7. **Kiểm tra kết nối nhanh (tuỳ chọn):** `scripts\check_java_connection.bat` kết nối, in `CONNECTED to <host>:<port>` rồi thoát với mã 0.

### Gửi telemetry giả (không cần Wokwi)

Dùng để kiểm tra broker + CLI khi chưa chạy simulator. Tạo file chứa JSON rồi publish bằng `--message-file` (tránh lỗi escape dấu nháy trong PowerShell):

```powershell
Set-Content -LiteralPath "$env:TEMP\sgu-telemetry.json" -Value '{"temp":28.5,"hum":65.0}' -Encoding UTF8 -NoNewline
java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar publish --config config/client.properties --topic sensor/data --message-file "$env:TEMP\sgu-telemetry.json"
```

Terminal subscribe phải in dòng `HH:mm:ss sensor/data {"temp":28.5,"hum":65.0}`. `--message` và `--message-file` loại trừ nhau; Java bỏ BOM UTF-8 ở đầu file nhưng gửi nguyên phần còn lại, nên dùng `-NoNewline` để payload không kèm ký tự xuống dòng (PowerShell 5.1 với `-Encoding UTF8` ghi BOM; điều đó không sao).

---

## 3. Chế độ 3 máy

Vai trò (gọi theo tên, không theo số):

- **Máy broker:** chạy broker Java, mở firewall cho TCP 1883.
- **Máy Wokwi:** có PlatformIO + Wokwi, chạy ESP32 mô phỏng. Đây là máy đã cài sẵn toolchain nên không đổi vai trò.
- **Máy dashboard/client:** chạy `subscribe_java.bat`, `publish_led.bat`, `check_java_connection.bat`. (Swing dashboard chưa có; hiện tại dùng CLI.)

Cả ba máy dùng **cùng commit** (`git rev-parse HEAD`) và nối cùng một mạng (hotspot). Hotspot có thể bật chế độ cô lập client (client isolation), khi đó các máy không thấy nhau dù cùng SSID.

### 3.1. Máy broker

```powershell
git pull --ff-only
git rev-parse HEAD
mvn clean verify
ipconfig
scripts\start_java_broker.bat
```

Ghi lại IPv4 hotspot của máy này (mục 1.3). Đây là `<IP broker>` dùng cho hai máy còn lại.

**Lần đầu Java lắng nghe, Windows Defender Firewall hiện hộp thoại "Allow access".** Bấm **Cancel** (không tick Public/Private), vì lựa chọn đó tạo rule cho `java.exe` mở cho cả mạng. Nếu lỡ cho phép, xoá rule tự tạo (tên thường là "OpenJDK Platform binary" hoặc "java.exe") trong Windows Defender Firewall with Advanced Security. Chỉ dựa vào rule thủ công giới hạn subnet bên dưới.

**Firewall (chạy trong một cửa sổ PowerShell Administrator riêng).** `scripts\start_java_broker.bat` giữ cửa sổ terminal của nó cho tới khi broker dừng, nên đừng gõ lệnh firewall vào đó. Rule chỉ cho phép subnet hotspot, không tắt firewall và không mở mọi địa chỉ:

```powershell
$brokerLanAddress = Read-Host 'IPv4 cua may broker tren hotspot'
$hotspotSubnet = Read-Host 'CIDR subnet hotspot, vi du 192.168.43.0/24'
New-NetFirewallRule -DisplayName 'SGU Java MQTT hotspot TCP1883' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 1883 -LocalAddress $brokerLanAddress -RemoteAddress $hotspotSubnet -Profile Any
```

Đổi cổng thì phải đổi cả `port=` trong `config/broker.properties`, `config/client.properties`, `MQTT_BROKER_PORT` trong firmware và rule firewall. Xoá rule sau buổi demo: `Remove-NetFirewallRule -DisplayName 'SGU Java MQTT hotspot TCP1883'`.

### 3.2. Máy Wokwi và máy dashboard: kiểm tra TCP trước

Trên **cả hai máy** (không giả định cùng SSID là liên lạc được):

```powershell
Test-NetConnection <IP broker> -Port 1883
```

Phải thấy `TcpTestSucceeded : True`. Nếu `False`, xem bảng xử lý lỗi ở mục 4 và sửa trước khi làm tiếp.

### 3.3. Máy Wokwi

1. Trong `esp32_wokwi/broker_config.h` đổi `MQTT_BROKER_HOST` thành `<IP broker>` (ví dụ `"172.20.10.2"`).
2. Build lại firmware: `pio run -e esp32doit-devkit-v1` (bắt buộc, IP được biên dịch vào firmware).
3. `F1` → **Wokwi: Start Simulator**. Giữ Wi-Fi `Wokwi-GUEST`, không nhập tên hotspot vào firmware.

### 3.4. Máy dashboard/client

1. Tạo `config/client.properties` từ file mẫu (mục 1.4) và đặt `host=<IP broker>`.
2. Kiểm tra: `scripts\check_java_connection.bat` (phải in `CONNECTED to <IP broker>:1883`).
3. `scripts\subscribe_java.bat`, và ở terminal khác `scripts\publish_led.bat ON` / `OFF`.

### 3.5. Bước kiểm tra Wokwi có ra được LAN không

Simulator Wokwi trong VS Code có gateway tích hợp, nhưng việc nó tới được IP LAN (không phải `host.wokwi.internal`) phụ thuộc bản Wokwi và license. Hãy kiểm tra, đừng giả định:

- Nếu ESP32 log `failed, rc=-2` liên tục **trong khi** `Test-NetConnection <IP broker> -Port 1883` trên máy Wokwi cho `True`: ghi lại hiện tượng này, rồi **chuyển broker sang máy Wokwi** (đặt `MQTT_BROKER_HOST "host.wokwi.internal"`, chạy broker trên máy Wokwi như chế độ 1 máy). Máy còn lại làm dashboard/client, `host=` là IPv4 của máy Wokwi (cần rule firewall tương tự trên máy Wokwi).
- Nếu ESP32 kết nối được (`connected to Java MQTT broker`): chế độ 3 máy đúng như thiết kế.

### 3.6. Dò cổng bằng `tools/spike/TcpProbe.java`

Công cụ nhỏ để tách lỗi mạng khỏi lỗi MQTT/broker. Nó lắng nghe `0.0.0.0:<port>`, in các IPv4 của máy, ghi lại mọi kết nối (peer và tối đa 32 byte đầu ở dạng hex), trả CONNACK `20 02 00 00` và giữ socket 30 giây. Một MQTT CONNECT bắt đầu bằng byte `10`.

```powershell
java tools/spike/TcpProbe.java 1883
```

Dùng khi **broker thật chưa chạy** (hai chương trình không cùng nghe được cổng 1883; có thể dùng cổng khác, ví dụ `18830`, nếu cổng 1883 đang bận). Cách dùng:

1. Trên máy broker chạy TcpProbe, mở firewall như mục 3.1.
2. Trên máy Wokwi và máy dashboard chạy `Test-NetConnection <IP broker> -Port 1883`.
3. Với Wokwi: đặt `MQTT_BROKER_HOST` là `<IP broker>`, build, Start Simulator; probe phải in `ACCEPT from ...` và `first N bytes: 10 ...`. Có `ACCEPT` nghĩa là ESP32 trong Wokwi đã tới được máy broker.
4. Đối chứng: khi không có probe/broker, ESP32 phải log `failed, rc=-2`. Dừng probe (Ctrl+C) sau khi xong, rồi mới chạy broker thật.

Wokwi gateway hỗ trợ TCP/UDP nhưng không hỗ trợ ICMP, nên `ping` không dùng để kiểm tra được.

---

## 4. Bảng xử lý lỗi

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| ESP32 log `failed, rc=-2` | Không kết nối TCP được tới broker: broker chưa chạy, sai `MQTT_BROKER_HOST`, firewall, hoặc Wokwi không ra được LAN | Kiểm tra broker đang chạy; với chế độ 1 máy dùng `host.wokwi.internal`; chế độ 3 máy chạy `Test-NetConnection`; build lại firmware sau khi đổi host; xem mục 3.5 |
| ESP32 log `rc=5` hoặc `rc=4` (CLI báo `Authentication failed`) | Sai username/password giữa broker, client, firmware | Đồng bộ `username`/`password` ở `config/broker.properties`, `config/client.properties`, `broker_config.h`; build lại firmware |
| `Port 1883 is already in use` (broker thoát mã 1) | Một chương trình khác giữ cổng 1883, thường là dịch vụ Mosquitto cũ trên Windows | Xem ai giữ cổng: `Get-NetTCPConnection -LocalPort 1883`, tìm tiến trình bằng `Get-Process -Id <OwningProcess>`. Nếu là Mosquitto (`mosquitto.exe`): chạy PowerShell Administrator `Stop-Service mosquitto` (hoặc `Set-Service mosquitto -StartupType Manual`) trước buổi demo. Hoặc đổi `port=` ở mọi file cấu hình và firewall |
| `Test-NetConnection` cho `TcpTestSucceeded : False` | Firewall máy broker chưa mở đúng subnet, sai IP/subnet, broker không chạy, hoặc hotspot bật client isolation | Kiểm tra rule firewall (mục 3.1), IPv4 đúng adapter hotspot, thử tắt client isolation trên hotspot, thử `java tools/spike/TcpProbe.java 1883` để cô lập lỗi mạng |
| `Missing config\client.properties` | Chưa copy file mẫu | `Copy-Item config\client.example.properties config\client.properties` rồi sửa `host=` |
| CLI báo `Duplicate option` | Truyền thêm tham số vào `subscribe_java.bat` hoặc `check_java_connection.bat` | Chạy script không kèm tham số; cần tuỳ chỉnh thì gọi trực tiếp `java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar ...` |
| CLI TCP pass nhưng Wokwi không kết nối | IP biên dịch vào firmware cũ, hoặc simulator nạp sai file `.bin` | Build lại firmware, kiểm tra đường dẫn firmware trong `wokwi.toml`, Start Simulator lại |

---

## 5. Giới hạn của MVP

- Không có QoS 1 chiều từ broker xuống client: mọi subscription được cấp QoS 0.
- Không có TLS và không có WebSocket.
- Tin retained (ví dụ `sensor/status`) chỉ nằm trong bộ nhớ, mất khi khởi động lại broker.
- Chưa có Swing dashboard; CLI Java (`check`, `subscribe`, `publish`) là client hiện tại.
- Kết quả kiểm tra: `docs/validation/single-machine-results.md`. Chỉ ghi "3 máy pass" khi có bằng chứng từ ba máy thật (tạo `docs/validation/YYYY-MM-DD-three-machine-results.md` với ngày chạy thật).
