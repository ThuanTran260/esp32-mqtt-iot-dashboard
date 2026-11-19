# Kết quả kiểm tra chế độ 1 máy

Ngày chạy: 2026-11-19. Nhánh `feat/java-mqtt-broker`, commit gốc khi chạy: `34324d6`.

Quy ước: `PASS` chỉ ghi khi đã quan sát thật; `NOT RUN` là chưa làm.

## Ghi chú môi trường (đọc trước)

- **Dùng cổng 18830 thay vì 1883.** Trên máy chạy kiểm tra, cổng 1883 đang bị dịch vụ Windows `mosquitto` (`mosquitto.exe`, PID 4640) giữ (`Get-NetTCPConnection -LocalPort 1883` trả về `127.0.0.1` và `::1`). Dịch vụ này không bị dừng. Broker Java được chạy với file cấu hình tạm `port=18830`, client trỏ cùng cổng. Kết luận kiểm tra này không phụ thuộc số cổng, nhưng cấu hình chuẩn của demo vẫn là 1883: trước buổi demo thật phải dừng dịch vụ Mosquitto (runbook, bảng xử lý lỗi).
- **Máy này không có adapter Wi-Fi.** Adapter đang hoạt động là Ethernet `Realtek Gaming 2.5GbE Family Controller`, IPv4 `192.168.1.100`. Theo thiết kế, client dùng IPv4 LAN thật của máy (không dùng `localhost` hay `0.0.0.0`), nên `host=192.168.1.100` trong config client tạm. Các adapter `vEthernet` (`172.19.64.1`, `169.254.33.67`) bị bỏ qua.
- Config tạm nằm ngoài repo (thư mục scratchpad), không commit. Tài khoản `user1`; mật khẩu không được ghi lại ở đây.
- Chạy ở mức phần mềm (broker + CLI). Không có Wokwi.

## A. Kiểm tra phần mềm

| Lệnh | Kết quả | Outcome |
|---|---|---|
| `mvn clean verify` | exit 0; `java-broker` 92 test, `java-desktop` 51 test, 0 fail, 0 error, 0 skip (tổng từ `target/surefire-reports`) | PASS |
| `pio run -e esp32doit-devkit-v1` | exit 0; `[SUCCESS] Took 34.06 seconds`; RAM 14.4% (47296 B), Flash 74.2% (972463 B); tạo `firmware.bin`/`firmware.elf` | PASS |
| `git diff --check` | không có output, exit 0 | PASS |

`pio` không có trong PATH trên máy này nên gọi bằng đường dẫn đầy đủ `C:/Users/Admin/.platformio/penv/Scripts/pio.exe run -e esp32doit-devkit-v1`.

## B. Chạy thử broker + CLI trên một máy (cổng 18830)

Broker (`java -jar java-broker/target/java-broker-1.0-SNAPSHOT.jar --config <broker tạm>`), output thật:

```text
Java MQTT broker listening on 0.0.0.0:18830
Connect clients to one of:
  Realtek Gaming 2.5GbE Family Controller: 192.168.1.100
  Hyper-V Virtual Ethernet Adapter: 172.19.64.1
  Hyper-V Virtual Ethernet Adapter #2: 169.254.33.67
```

Terminal subscribe: `java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar subscribe --config <client tạm> --topic "sensor/#" --seconds 20`. Terminal khác lần lượt chạy:

```text
check                                                     -> CONNECTED to 192.168.1.100:18830   (exit 0)
publish --topic sensor/data --message-file <telemetry.json> (exit 0)
publish --topic sensor/led --message ON                     (exit 0)
publish --topic sensor/led --message OFF                    (exit 0)
check với mật khẩu sai                                      -> Authentication failed              (exit 1)
```

`telemetry.json` chứa đúng `{"temp":28.5,"hum":65.0}` (tạo bằng `Set-Content -NoNewline`). Output của terminal subscribe (lần chạy có `-NoNewline`):

```text
Listening on sensor/# for 12 s        (stderr)
21:57:12 sensor/data {"temp":28.5,"hum":65.0}
```

Lần chạy đầu (file tạo không có `-NoNewline`) subscribe in thêm `\r\n` ở cuối payload (CLI hiển thị ký tự xuống dòng dạng `\r\n`), cùng với `sensor/led ON` và `sensor/led OFF`:

```text
21:56:31 sensor/data {"temp":28.5,"hum":65.0}\r\n
21:56:32 sensor/led ON
21:56:32 sensor/led OFF
```

Vì vậy runbook dùng `-NoNewline` khi tạo file payload. Sau khi chạy xong, tiến trình broker và subscriber do mình khởi động đã được dừng theo PID; dịch vụ Mosquitto (PID 4640) vẫn chạy.

| Hạng mục | Outcome |
|---|---|
| Broker Java khởi động, in địa chỉ nghe và danh sách IPv4 | PASS |
| `check` tới IPv4 LAN của máy, thoát 0 | PASS |
| `publish --message-file` -> subscribe thấy đúng `{"temp":28.5,"hum":65.0}` | PASS |
| `publish sensor/led ON` / `OFF` -> subscribe thấy cả hai | PASS |
| Sai mật khẩu -> `Authentication failed`, exit 1, không in mật khẩu | PASS |

## C. Chạy thử với Wokwi (cần người bấm Start Simulator)

Chưa chạy: các mục này cần người mở VS Code và nhấn `F1` -> Wokwi: Start Simulator. Không có ô nào được điền PASS khi chưa quan sát.

| Hạng mục | Mong đợi | Outcome |
|---|---|---|
| ESP32 kết nối broker | Serial log `connected to Java MQTT broker` | NOT RUN |
| Trạng thái thiết bị | subscribe thấy `sensor/status ONLINE` | NOT RUN |
| Telemetry | subscribe thấy `sensor/data` khoảng mỗi 3 s | NOT RUN |
| Đổi giá trị DHT22 trong Wokwi | Số trong `sensor/data` đổi theo | NOT RUN |
| `publish_led.bat ON` / `OFF` | LED trong Wokwi đổi trạng thái | NOT RUN |
| Dừng simulator đột ngột | Trong tối đa 25 s thấy `sensor/status OFFLINE` | NOT RUN |
| Sai password ở client | CLI in `Authentication failed` | NOT RUN (đã thử ở mức CLI, xem mục B; chưa thử với ESP32) |
| Firmware khi broker chưa chạy | Log `failed, rc=...`, thử lại mỗi 5 s | NOT RUN |

## D. Chế độ 3 máy

Chưa có bằng chứng từ ba máy thật. Không ghi "3 máy pass". Khi chạy thật, tạo `docs/validation/YYYY-MM-DD-three-machine-results.md` theo `docs/java-runbook.md` phần 3.
