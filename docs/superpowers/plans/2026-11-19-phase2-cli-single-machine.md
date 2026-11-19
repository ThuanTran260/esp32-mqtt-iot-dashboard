# Giai đoạn 2: đóng gói broker, firmware LAN, CLI Java, chạy 1 máy → 3 máy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Biến broker Java đã có thành JAR chạy được, trỏ firmware Arduino tới broker qua file cấu hình, thêm CLI Java (Paho) và chạy trọn luồng trên một máy; chuyển sang ba máy chỉ bằng đổi IP.

**Architecture:** Module `java-broker` (đã có, A1–A4) thêm entry point `BrokerMain`. Module mới `java-desktop` chứa transport Paho dùng chung (cho CLI bây giờ và Swing sau) và CLI. Firmware đọc địa chỉ broker từ `broker_config.h` (gitignored), file mẫu mặc định là `host.wokwi.internal` cho chế độ 1 máy.

**Tech Stack:** JDK 21+ (máy hiện có JDK 24), Maven 3.9+, JUnit 5.13.4, Eclipse Paho 1.2.5, maven-jar-plugin 3.4.2, maven-shade-plugin 3.6.2, PlatformIO + PubSubClient 2.8.

**Spec:** `docs/superpowers/specs/2026-11-14-java-mqtt-hotspot-design.md` — đọc cả mục "Sửa đổi phạm vi MVP" và "Giai đoạn 2".

## Trạng thái bàn giao (đọc trước khi làm)

- Branch `feat/java-mqtt-broker`, đã push tới `ace2b28`. Lấy về: `git fetch origin && git switch feat/java-mqtt-broker`.
- Xong và đã review: A1 framing, A2 codec/topic, A3 listener/auth/keepalive, A4 routing/retained/PUBACK QoS1 vào/Will. `mvn -pl java-broker test` báo 87 test pass (theo báo cáo worker).
- API đang có (dùng nguyên, không viết lại): `BrokerConfig(String bindAddress,int port,String username,String password)`, `BrokerConfig.load(Path)` (khóa `bindAddress`/`port`/`username`/`password`, thiếu khóa lấy mặc định, port file 1..65535, lỗi → `IOException`), `BrokerServer(BrokerConfig)`, `start()`, `port()`, `close()`. Test fixture `RunningBroker` và `RawClient` trong `java-broker/src/test/java/vn/edu/sgu/mqtt/`.
- Spike S0 còn trong code: khối `#ifdef SPIKE_HOST` ở `esp32_wokwi/sketch.ino:12-19`, `[env:spike]` ở `platformio.ini:19-24`, `tools/spike/TcpProbe.java`, `docs/spike-wokwi-tcp.md`. Task 2 gỡ code spike; giữ `TcpProbe.java` làm công cụ kiểm tra mạng.
- Lỗi nhỏ đã ghi nhận để xử lý sau (không chặn plan này): Will có thể mất trong khoảnh khắc giữa register và CONNACK; retained replay có thể đến sau giá trị live mới hơn; không có write timeout; thiếu test Will khi keepalive hết hạn.
- Lần build `pio run -e spike` gần nhất trên máy cũ báo FAILED, chưa xem nguyên nhân (worker spike trước đó build được cả hai env). Task 2 phải build và đọc lỗi nếu có.

## Global Constraints

- Phần mềm PC dùng Java 21 trở lên; Maven compiler release=21; firmware ESP32 dùng Arduino/C++.
- Broker MQTT tự viết bằng Java chuẩn; không nhúng Mosquitto, HiveMQ, Moquette hoặc broker có sẵn.
- Eclipse Paho 1.2.5 chỉ dùng làm client Java và client kiểm thử; không xử lý broker.
- Bind mặc định của broker là 0.0.0.0:1883; client nhập IPv4 thật của máy broker, không nhập 0.0.0.0.
- Topic chuẩn là sensor/data, sensor/led, sensor/status; không còn namespace thuantran260/.
- DHT22 dùng GPIO16, LED dùng GPIO4, telemetry mỗi 3000 ms; giữ sơ đồ phần cứng hiện tại.
- Tài khoản demo là user1/123456, cấu hình được; không log mật khẩu hoặc commit key Wokwi.
- Payload telemetry: `{"temp":28.5,"hum":65.0}` (số thật, không phải chuỗi); sensor/led là `ON`/`OFF`; sensor/status là `ONLINE`/`OFFLINE` retained.
- File cục bộ không commit: `config/broker.properties`, `config/client.properties`, `esp32_wokwi/broker_config.h`, `**/target/`.
- Chỉ ghi "3 máy pass" khi có bằng chứng từ ba máy thật.

## Review Focus

- Port 1883 đang bị chiếm → broker phải thoát mã 1 với thông báo rõ cổng, không treo, không dừng tiến trình khác (test ở Task 1).
- Thiếu file config hoặc config sai (port chữ, ngoài dải) → thông báo một dòng, exit 1, không in stack trace dài và không in password (Task 1, Task 4).
- CLI tới IP không có broker → `check` thoát trong ≤ 6 s với exit 1, không treo (Task 3, Task 4).
- Sai password → CLI exit 1 và in lý do "authentication failed", không in password (Task 4).
- Firmware khi broker chưa chạy → log `failed, rc=...` và thử lại mỗi 5 s, vẫn đọc DHT bình thường khi kết nối lại (kiểm tra thủ công ở Task 5).

---

### Task 1: Broker chạy được bằng `java -jar`

**Files:**
- Create: `java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerMain.java`
- Create: `java-broker/src/main/java/vn/edu/sgu/mqtt/server/LanAddresses.java`
- Create: `config/broker.example.properties`
- Create: `scripts/start_java_broker.bat`
- Modify: `java-broker/pom.xml` (maven-jar-plugin 3.4.2, Main-Class `vn.edu.sgu.mqtt.server.BrokerMain`)
- Modify: `.gitignore` (thêm `config/broker.properties`, `config/client.properties`, `esp32_wokwi/broker_config.h`)
- Test: `java-broker/src/test/java/vn/edu/sgu/mqtt/BrokerMainTest.java`

**Interfaces:**
- Consumes: `BrokerConfig.load(Path)`, `BrokerServer(BrokerConfig)`, `start()`, `port()`, `close()`.
- Produces: `static int BrokerMain.run(String[] args, PrintStream out, PrintStream err)` trả 0 (`--help`), 1 (lỗi config/bind), 2 (sai cú pháp tham số); khi khởi động thành công thì chặn tới khi bị dừng. `main` chỉ gọi `run` và `System.exit` nếu khác 0. Để test được, có thêm `static BrokerServer BrokerMain.startFromArgs(String[] args, PrintStream out)` trả server đã start (ném `IOException`/`IllegalArgumentException`).
- Produces: `static List<String> LanAddresses.ipv4()` — IPv4 của adapter đang up, không loopback, mỗi phần tử dạng `"<interface display name>: <ip>"`.
- Produces JAR: `java-broker/target/java-broker-1.0-SNAPSHOT.jar`.

- [ ] **Step 1: Viết test**

```java
@Test void helpExitsZeroAndMentionsConfig() {
    var out = new ByteArrayOutputStream();
    assertEquals(0, BrokerMain.run(new String[]{"--help"}, new PrintStream(out), System.err));
    assertTrue(out.toString().contains("--config"));
}
@Test void unknownFlagExitsTwo() {
    assertEquals(2, BrokerMain.run(new String[]{"--bogus"}, System.out, new PrintStream(new ByteArrayOutputStream())));
}
@Test void missingConfigFileExitsOneWithoutStackTrace(@TempDir Path dir) {
    var err = new ByteArrayOutputStream();
    assertEquals(1, BrokerMain.run(new String[]{"--config", dir.resolve("nope.properties").toString()},
        System.out, new PrintStream(err)));
    assertFalse(err.toString().contains("\tat "));
}
@Test void portInUseExitsOne(@TempDir Path dir) throws Exception {
    try (var busy = new ServerSocket(0)) {
        Path f = dir.resolve("b.properties");
        Files.writeString(f, "bindAddress=127.0.0.1\nport=" + busy.getLocalPort() + "\npassword=secret-xyz\n");
        var err = new ByteArrayOutputStream();
        assertEquals(1, BrokerMain.run(new String[]{"--config", f.toString()}, System.out, new PrintStream(err)));
        assertTrue(err.toString().contains(String.valueOf(busy.getLocalPort())));
        assertFalse(err.toString().contains("secret-xyz"));
    }
}
```

Thêm test `startsFromConfigAndPrintsNoPassword`: lấy port trống bằng `new ServerSocket(0)` rồi đóng (`load()` từ chối port 0), ghi file `bindAddress=127.0.0.1`, `port=<n>`, `password=secret-xyz`; gọi `startFromArgs`; Paho `tcp://127.0.0.1:<n>` với user1/secret-xyz kết nối thành công; output không chứa `secret-xyz`; `close()` trong finally.

- [ ] **Step 2: Chạy red** — `mvn -pl java-broker "-Dtest=BrokerMainTest" test` → lỗi biên dịch vì chưa có `BrokerMain`.

- [ ] **Step 3: Implement `BrokerMain` và `LanAddresses`**

Tham số: `--help`, `--config <path>` (bắt buộc khi không `--help`). Khi start in: `Java MQTT broker listening on <bind>:<port>`, rồi `Connect clients to one of:` và từng dòng `LanAddresses.ipv4()`. Bắt `BindException` → `Port <n> is already in use. Stop the other program or change port in the config.` exit 1. Shutdown hook gọi `server.close()`. Không in đối tượng config (dù `toString` đã che password, chỉ in bind/port).

- [ ] **Step 4: File config và script**

`config/broker.example.properties`:
```properties
bindAddress=0.0.0.0
port=1883
username=user1
password=123456
```
`scripts/start_java_broker.bat`: `cd /d "%~dp0.."`; nếu thiếu `config\broker.properties` thì `copy config\broker.example.properties config\broker.properties` và báo đã tạo; chạy `java -jar "java-broker\target\java-broker-1.0-SNAPSHOT.jar" --config "config\broker.properties"`; `exit /b %ERRORLEVEL%`.

- [ ] **Step 5: Chạy green và kiểm tra JAR**

```powershell
mvn -pl java-broker clean verify
java -jar java-broker/target/java-broker-1.0-SNAPSHOT.jar --help
```
Expected: toàn bộ test pass; `--help` in usage, exit 0. Chạy `scripts\start_java_broker.bat`, thấy dòng `listening on 0.0.0.0:1883` và danh sách IPv4; Ctrl+C dừng sạch. Ghi output vào report.

- [ ] **Step 6: Commit** — `git add java-broker config/broker.example.properties scripts/start_java_broker.bat .gitignore` rồi `git commit -m "feat(java-broker): runnable JAR with config file and LAN address hints"`.

### Task 2: Firmware đọc địa chỉ broker từ file cấu hình, gỡ spike

**Files:**
- Create: `esp32_wokwi/broker_config.example.h`
- Modify: `esp32_wokwi/sketch.ino` (khối cấu hình dòng 1-30, `reconnect()`, phần publish trong `loop()`)
- Modify: `platformio.ini` (xóa `[env:spike]` và comment spike)
- Delete: `docs/spike-wokwi-tcp.md` (nội dung hữu ích chuyển vào runbook ở Task 5)
- Giữ: `tools/spike/TcpProbe.java`

**Interfaces:**
- Produces macros: `MQTT_BROKER_HOST`, `MQTT_BROKER_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD`.

- [ ] **Step 1: Tạo `broker_config.example.h`**

```cpp
#pragma once
// Chế độ 1 máy: broker chạy trên chính máy Wokwi.
// Chế độ 3 máy: thay bằng IPv4 hotspot của máy broker, ví dụ "172.20.10.2".
#define MQTT_BROKER_HOST "host.wokwi.internal"
#define MQTT_BROKER_PORT 1883
#define MQTT_USERNAME "user1"
#define MQTT_PASSWORD "123456"
```

- [ ] **Step 2: Sửa `sketch.ino`**

Thay khối cấu hình (gồm cả khối SPIKE) bằng:
```cpp
#if __has_include("broker_config.h")
#include "broker_config.h"
#else
#error "Copy esp32_wokwi/broker_config.example.h to esp32_wokwi/broker_config.h and set MQTT_BROKER_HOST"
#endif
```
và `mqtt_server = MQTT_BROKER_HOST`, `mqtt_port = MQTT_BROKER_PORT`, `mqtt_user = MQTT_USERNAME`, `mqtt_pass = MQTT_PASSWORD`, `topic_data = "sensor/data"`, `topic_led = "sensor/led"`, `topic_status = "sensor/status"`. Trong `reconnect()`: log `connected to Java MQTT broker`, publish ONLINE retained một lần, subscribe `topic_led` một lần (bỏ các lần publish/subscribe `"sensor/..."` trùng). Callback chỉ so `topic_led`. Trong `loop()`: `doc["temp"] = data.temperature; doc["hum"] = data.humidity;` (số thật), publish `topic_data` một lần, `topic_status` ONLINE retained một lần, kiểm tra giá trị trả về của `publish` và log khi false. Trong `setup()`: `client.setKeepAlive(15); client.setSocketTimeout(5);`. Giữ GPIO, `Wokwi-GUEST`, chu kỳ 3000 ms.

- [ ] **Step 3: Build**

```powershell
Copy-Item esp32_wokwi/broker_config.example.h esp32_wokwi/broker_config.h   # chỉ khi chưa có
pio run -e esp32doit-devkit-v1
```
Expected: `SUCCESS`. Nếu FAILED, đọc dòng `error:` đầu tiên và sửa; ghi nguyên nhân vào report. Kiểm tra thêm: tạm đổi tên `broker_config.h` → build phải dừng ở thông báo `#error` trên; đổi lại.

- [ ] **Step 4: Commit** — `git add esp32_wokwi/sketch.ino esp32_wokwi/broker_config.example.h platformio.ini docs/spike-wokwi-tcp.md` rồi `git commit -m "feat(firmware): read broker address from broker_config.h, use standard topics"`.

### Task 3: Module `java-desktop` với transport Paho dùng chung

Theo Task B1 trong `docs/superpowers/plans/2026-11-14-java-desktop-hotspot.md` (đọc mục đó làm yêu cầu chi tiết), với các điều chỉnh sau:

**Files:** như B1: `java-desktop/pom.xml`, `pom.xml` (thêm module), `java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/{ConnectionSettings,ConnectionState,MqttTransport,PahoTransport}.java`, test `LocalBrokerFixture.java`, `PahoTransportTest.java`.

**Interfaces:** đúng như B1 (`MqttTransport` với `Listener`, `connect/disconnect/publish/isConnected/close`; `PahoTransport()` và `PahoTransport(Map<String,Integer>)`; `ConnectionSettings(host,port,username,password)`, `uri()`, `load(Path)` với khóa `host`, `port`, `username`, `password`).

Điều chỉnh so với B1:
- Broker MVP cấp QoS0 cho mọi subscription; `PahoTransport` không được coi grant 0 là lỗi (chỉ grant `0x80` mới là lỗi).
- `connect` phải hoàn tất hoặc thất bại trong tối đa 5 s (`setConnectionTimeout(5)`), kể cả khi host không có broker.
- Test thêm: host không có broker (`127.0.0.1` + port trống) → future thất bại trong ≤ 6 s; sai password → future thất bại, `onError` không chứa password.
- `LocalBrokerFixture` dựng `new BrokerServer(new BrokerConfig("127.0.0.1", 0, "user1", "123456"))`.

- [ ] Step 1–5 như B1 (test → red → implement → `mvn -pl java-desktop -am test` green → commit `feat(java-client): add Paho MQTT transport`).

### Task 4: CLI Java `check` / `subscribe` / `publish` và JAR client

Theo Task B4 trong `docs/superpowers/plans/2026-11-14-java-desktop-hotspot.md`, với các điều chỉnh:

- Chưa có Swing: mode `dashboard` in `Dashboard UI is not available yet; use check/subscribe/publish` và exit 2. Không tạo `scripts/start_java_dashboard.bat` ở task này.
- Bỏ `PackagedClientTest` và profile packaged-smoke; thay bằng kiểm tra thủ công trong report: `java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar --help` exit 0.
- `subscribe` in mỗi tin một dòng: `<HH:mm:ss> <topic> <payload UTF-8>`.
- `check` in `CONNECTED to <host>:<port>` khi thành công; lỗi kết nối in `Cannot reach broker at <host>:<port>` hoặc `Authentication failed`, exit 1.
- Thêm `scripts/check_java_connection.bat`, `scripts/subscribe_java.bat` (`subscribe --topic "sensor/#" --seconds 3600`) và `scripts/publish_led.bat ON|OFF`; mỗi script báo thiếu `config\client.properties` trước khi gọi java.
- `config/client.example.properties`: `host=172.20.10.2` kèm comment "đổi thành IPv4 Wi-Fi của máy broker; chế độ 1 máy dùng IPv4 Wi-Fi của chính máy này", `port=1883`, `username=user1`, `password=123456`.

Test (theo B4): parse publish/unknown option/duplicate flag/thiếu value (exit 2); `--message` và `--message-file` loại trừ nhau; file >65536 byte bị từ chối; bỏ BOM; `check` với `FakeTransport` connected → exit 0 và in `CONNECTED`; connect lỗi → exit 1; `publish` gọi đúng topic/payload một lần; `subscribe --seconds 1` kết thúc ≤ 3 s, exit 0. `FakeTransport` định nghĩa như B3 (field `connected`, `listener`, `sent`).

- [ ] Step 1–5 như B4 (test → red → implement → `mvn clean verify` green và `--help` thủ công → commit `feat(java-client): MQTT CLI and launcher scripts`).

### Task 5: Hướng dẫn chạy 1 máy và 3 máy, chạy thử 1 máy

**Files:**
- Create: `docs/java-runbook.md`
- Create: `docs/validation/single-machine-results.md` (mẫu, mọi kết quả ban đầu `NOT RUN`)
- Modify: `README.md`, `PROJECT.md`, `HUONG_DAN_CHAY_3_MAY.md` (viết lại theo Java; bỏ Mosquitto, web dashboard, HiveMQ, script Python; link tới runbook)

- [ ] **Step 1: Viết `docs/java-runbook.md`** với ba phần:
  1. **Chuẩn bị chung:** JDK 21+, Maven, `mvn clean verify`, PlatformIO + Wokwi trên máy Wokwi; cách xem IPv4 Wi-Fi (`ipconfig`, adapter Wi-Fi, bỏ qua vEthernet/VPN/VirtualBox).
  2. **Chế độ 1 máy:** terminal 1 `scripts\start_java_broker.bat`; `broker_config.h` để `host.wokwi.internal`; `pio run -e esp32doit-devkit-v1`; F1 → Wokwi: Start Simulator; `config\client.properties` `host=<IPv4 Wi-Fi của máy này>`; terminal 2 `scripts\subscribe_java.bat`; terminal 3 `scripts\publish_led.bat ON` / `OFF`.
  3. **Chế độ 3 máy:** máy broker chạy broker + rule firewall chỉ cho subnet hotspot (lệnh `New-NetFirewallRule` có `-RemoteAddress <subnet>` như B5 Step 2); máy Wokwi và máy dashboard chạy `Test-NetConnection <IP broker> -Port 1883`; đổi `MQTT_BROKER_HOST` và `host=` thành IP máy broker; build lại firmware. **Bước kiểm tra Wokwi ra LAN:** nếu ESP32 log `failed, rc=-2` liên tục trong khi `Test-NetConnection` True, ghi lại và chuyển broker sang máy Wokwi (dùng `host.wokwi.internal`), máy còn lại làm dashboard. Gồm cả cách dùng `tools/spike/TcpProbe.java` để thử cổng.
  Bảng xử lý lỗi: `rc=-2` (không tới được), `rc=5`/`rc=4` (sai tài khoản), `Port ... already in use`, `TcpTestSucceeded False` (firewall/hotspot isolation).

- [ ] **Step 2: Viết lại README/PROJECT/HUONG_DAN_CHAY_3_MAY** — kiến trúc: ESP32 (Arduino, PubSubClient) ⇄ broker Java tự viết (TCP 1883) ⇄ CLI Java (Paho); bảng topic/payload trong Global Constraints; vai trò máy gọi theo tên; giới hạn MVP (không QoS1 outbound, không TLS/WebSocket, retained mất khi restart broker).

- [ ] **Step 3: Chạy kiểm tra phần mềm**

```powershell
mvn clean verify
pio run -e esp32doit-devkit-v1
git diff --check
```
Expected: tất cả pass. Sau đó trên một máy: chạy broker, `subscribe_java.bat`, và trong terminal khác `java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar publish --config config/client.properties --topic sensor/data --message-file <file chứa {"temp":28.5,"hum":65.0}>` → terminal subscribe thấy đúng dòng đó. Ghi output vào `single-machine-results.md`.

- [ ] **Step 4: Chạy thử 1 máy với Wokwi (cần người bấm Start Simulator)** — điền bảng: ESP32 log `connected to Java MQTT broker`; subscribe thấy `sensor/status ONLINE` và `sensor/data` mỗi ~3 s; đổi giá trị DHT22 trong Wokwi → số thay đổi; `publish_led.bat ON`/`OFF` → LED Wokwi đổi; dừng simulator đột ngột → trong ≤ 25 s thấy `sensor/status OFFLINE`; sai password ở client → `Authentication failed`. Ô nào chưa làm giữ `NOT RUN`; không điền PASS khi chưa quan sát.

- [ ] **Step 5: Commit và push**

```powershell
git add docs/java-runbook.md docs/validation/single-machine-results.md README.md PROJECT.md HUONG_DAN_CHAY_3_MAY.md
git commit -m "docs: Java runbook for single-machine and three-machine demo"
git push
```

## Sau plan này

- Swing dashboard: Task B2 (model) và B3 (UI) trong `2026-11-14-java-desktop-hotspot.md`, rồi bật lại mode `dashboard`.
- Nghiệm thu 3 máy thật theo runbook phần 3; tạo `docs/validation/YYYY-MM-DD-three-machine-results.md` với ngày chạy thật.
- Các lỗi nhỏ đã hoãn (mục "Trạng thái bàn giao").
