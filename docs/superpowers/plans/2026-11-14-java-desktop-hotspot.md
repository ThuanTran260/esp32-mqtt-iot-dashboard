# Java Desktop and Hotspot Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo ứng dụng giám sát/điều khiển và CLI bằng Java, ghép với broker Java và Arduino Wokwi để nghiệm thu ba máy cùng hotspot.

**Architecture:** Module Maven java-desktop dùng Eclipse Paho ở phía client, Swing cho UI và Gson đọc telemetry. Transport MQTT, model trạng thái và Swing view có interface riêng để kiểm thử mạng và logic không phụ thuộc cửa sổ. Broker production là module tự viết ở plan A, không phải Paho.

**Tech Stack:** JDK21+, Maven3.9+, Swing, Eclipse Paho1.2.5, Gson2.13.2, JUnit Jupiter5.13.4, maven-shade-plugin3.6.2.

## Global Constraints

- Phần mềm PC dùng Java 21 trở lên; Maven compiler release=21; firmware ESP32 dùng Arduino/C++.
- Broker MQTT tự viết bằng Java chuẩn; không nhúng Mosquitto, HiveMQ, Moquette hoặc broker có sẵn.
- Eclipse Paho 1.2.5 chỉ dùng làm client Java và client kiểm thử; không xử lý broker.
- Ba máy cùng hotspot; client kết nối IPv4 LAN của máy broker; bind mặc định của broker là 0.0.0.0:1883.
- Topic chuẩn là sensor/data, sensor/led, sensor/status; bỏ publish/subscribe trùng namespace public khỏi luồng chạy mới.
- DHT22 dùng GPIO16, LED dùng GPIO4, telemetry mỗi 3000 ms; giữ sơ đồ phần cứng hiện tại.
- Tài khoản demo là user1/123456, cấu hình được; không log mật khẩu hoặc commit key Wokwi.
- Kiểm thử tự động một máy và nghiệm thu thực tế ba máy được báo cáo riêng; chỉ xác nhận ba máy sau khi có bằng chứng từ cả ba.

---

## Phạm vi và điều kiện đầu vào

Đọc [thiết kế](../specs/2026-11-14-java-mqtt-hotspot-design.md) và hoàn thành [plan broker A1–A6](2026-11-14-java-mqtt-broker.md). Scope desktop là cách hiểu được nêu rõ của “full Java”; user đã cho phép firmware Arduino. Không có thay đổi mã nào được thực hiện khi viết plan này.

Các lựa chọn UI đã cân nhắc: Swing có sẵn trong JDK nên được chọn; JavaFX cần bổ sung runtime; giữ web làm UI chính không phù hợp cách hiểu đầy đủ của yêu cầu Java. Desktop không dùng WebSocket, không cần mở9001/8000.

## File structure và trách nhiệm

| File | Trách nhiệm |
|---|---|
| java-desktop/pom.xml | Paho/Gson runtime, JUnit và broker test dependency, shaded JAR |
| java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/ConnectionSettings.java | Cấu hình client và URI |
| java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/ConnectionState.java | Trạng thái broker |
| java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/MqttTransport.java | Interface mạng cho controller/CLI |
| java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/PahoTransport.java | Paho async, subscribe/reconnect/close |
| java-desktop/src/main/java/vn/edu/sgu/iot/model/Telemetry.java | Parse/validate JSON DHT22 |
| java-desktop/src/main/java/vn/edu/sgu/iot/model/DeviceState.java | UNKNOWN/ONLINE/OFFLINE/STALE |
| java-desktop/src/main/java/vn/edu/sgu/iot/model/LogEntry.java | Dòng log không chứa credential |
| java-desktop/src/main/java/vn/edu/sgu/iot/model/DashboardSnapshot.java | Dữ liệu immutable render UI |
| java-desktop/src/main/java/vn/edu/sgu/iot/model/DashboardModel.java | State transitions, stale và log50 |
| java-desktop/src/main/java/vn/edu/sgu/iot/ui/DashboardView.java | Interface render/showError |
| java-desktop/src/main/java/vn/edu/sgu/iot/ui/DashboardController.java | Điều phối transport/model/view |
| java-desktop/src/main/java/vn/edu/sgu/iot/ui/SwingDashboard.java | Form kết nối, gauges, LED, log |
| java-desktop/src/main/java/vn/edu/sgu/iot/ui/GaugePanel.java | Vẽ gauge có giới hạn min/max |
| java-desktop/src/main/java/vn/edu/sgu/iot/ClientMain.java | Entry point dashboard/check/publish/subscribe |
| java-desktop/src/main/java/vn/edu/sgu/iot/cli/ClientOptions.java | Parse CLI nghiêm ngặt |
| java-desktop/src/main/java/vn/edu/sgu/iot/cli/ClientCommands.java | CLI qua cùng transport |
| java-desktop/src/test/java/vn/edu/sgu/iot/ | Transport/model/controller/process tests |
| config/client.example.properties | Mẫu IPv4/port/credential client |
| scripts/start_java_dashboard.bat | Launcher UI Java |
| scripts/check_java_connection.bat | Kiểm tra MQTT auth/subscription từ máy khác |
| docs/java-hotspot-runbook.md | Hướng dẫn theo từng máy, firewall, nghiệm thu |
| docs/validation/three-machine-template.md | Biên bản nghiệm thu chưa điền kết quả |

## Task B1: Client MQTT async và test độc lập UI

**Files:**
- Create: java-desktop/pom.xml.
- Modify: pom.xml, thêm module java-desktop sau java-broker.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/ConnectionSettings.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/ConnectionState.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/MqttTransport.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/mqtt/PahoTransport.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/LocalBrokerFixture.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/PahoTransportTest.java.

**Interfaces:**
- Consumes from plan A: public BrokerConfig(String,int,String,String), BrokerServer(BrokerConfig), start(), port(), close().
- Produces: ConnectionSettings(String host,int port,String username,String password), String uri(), static load(Path) throws IOException; reject host rỗng/0.0.0.0 và port ngoài1..65535; không log password.
- Produces: ConnectionState enum DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR.
- Produces: interface MqttTransport extends AutoCloseable:

~~~java
interface Listener {
    void onConnection(ConnectionState state);
    void onMessage(String topic, byte[] payload);
    void onError(String message);
}
CompletionStage<Void> connect(ConnectionSettings settings, Listener listener);
CompletionStage<Void> disconnect();
CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain);
boolean isConnected();
void close();
~~~

- Produces: PahoTransport implements MqttTransport; constructor public no-arg dùng ba topic chuẩn, và PahoTransport(Map<String,Integer> startupSubscriptions) cho CLI subscribe filter riêng; map được defensive copy, không rỗng, QoS0/1.
- Produces test fixture LocalBrokerFixture.open(), settings(), uri(), close(); loopback port0, demo auth. Fixture tạo BrokerServer theo plan A; không tái sử dụng test fixture package-private của module broker.

- [ ] **Step 1: Tạo module/dependencies và transport integration test**

Kế thừa parent groupId vn.edu.sgu, version1.0-SNAPSHOT; artifactId java-desktop. Compile dependencies Paho1.2.5 và Gson2.13.2; test dependencies JUnit5.13.4 và vn.edu.sgu:java-broker:1.0-SNAPSHOT. java-desktop không có broker trong runtime JAR.

~~~java
@Test
void receivesTelemetryThroughJavaBroker() throws Exception {
    try (var fixture = LocalBrokerFixture.open();
         var transport = new PahoTransport();
         var source = new MqttClient(fixture.uri(), "source", new MemoryPersistence())) {
        var messages = new LinkedBlockingQueue<String>();
        var errors = new LinkedBlockingQueue<String>();
        MqttTransport.Listener listener = new MqttTransport.Listener() {
            public void onConnection(ConnectionState state) {}
            public void onMessage(String topic, byte[] bytes) {
                messages.add(topic + ":" + new String(bytes, StandardCharsets.UTF_8));
            }
            public void onError(String message) { errors.add(message); }
        };
        transport.connect(fixture.settings(), listener)
            .toCompletableFuture().get(3, TimeUnit.SECONDS);
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName("user1");
        options.setPassword("123456".toCharArray());
        source.connect(options);
        source.publish("sensor/data", "{\"temp\":28.5,\"hum\":65.0}".getBytes(
            StandardCharsets.UTF_8), 0, false);
        assertEquals("sensor/data:{\"temp\":28.5,\"hum\":65.0}",
            messages.poll(3, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty());
        source.disconnect();
    }
}
~~~

Callback lỗi đưa vào queue để assertion ở test thread; không chỉ fail trên Paho thread rồi để test vẫn pass.

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-desktop -am "-Dtest=PahoTransportTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: transport chưa compile hoặc subscribe chưa hoàn thành.

- [ ] **Step 3: Implement Paho connection và resubscribe**

~~~java
var options = new MqttConnectOptions();
options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
options.setCleanSession(true);
options.setUserName(settings.username());
options.setPassword(settings.password().toCharArray());
options.setAutomaticReconnect(true);
options.setKeepAliveInterval(15);
options.setConnectionTimeout(5);
var client = new MqttAsyncClient(settings.uri(),
    "java-ui-" + UUID.randomUUID(), new MemoryPersistence());
~~~

- [ ] **Step 3a: Ghép callback SUBACK và reconnect**

MqttCallbackExtended.connectComplete luôn subscribe lại startupSubscriptions; constructor mặc định là ba topic với QoS {0,0,1}. Chỉ phát CONNECTED và complete connect future sau SUBACK thành công. Callback messageArrived clone byte[] rồi chuyển listener. connectionLost phát RECONNECTING; initial auth fail phát ERROR và completeExceptionally, không loop auth vô hạn.

~~~java
var entries = new ArrayList<>(startupSubscriptions.entrySet());
String[] topics = entries.stream().map(Map.Entry::getKey).toArray(String[]::new);
int[] qos = entries.stream().mapToInt(Map.Entry::getValue).toArray();
client.subscribe(topics, qos, null, new IMqttActionListener() {
    public void onSuccess(IMqttToken token) {
        listener.onConnection(ConnectionState.CONNECTED);
        firstConnection.complete(null);
    }
    public void onFailure(IMqttToken token, Throwable error) {
        listener.onConnection(ConnectionState.ERROR);
        listener.onError(error.getClass().getSimpleName());
        firstConnection.completeExceptionally(error);
    }
});
~~~

- [ ] **Step 3b: Serialize lifecycle và future publish/disconnect**

firstConnection là CompletableFuture<Void> của lần connect đầu. Mỗi connect tạo generation counter; callback của client cũ bị bỏ qua sau disconnect/connect mới. publish future chỉ complete khi Paho action listener thành công; khi offline trả failedFuture(new IllegalStateException("Broker disconnected")). disconnect hủy reconnect, đóng client cũ trên worker, phát DISCONNECTED một lần. close idempotent, đóng callback resources; không block Swing EDT.

- [ ] **Step 4: Chạy green và tests lỗi**

Thêm test wrong password, host không kết nối được với timeout hữu hạn, broker restart cùng port → reconnect/resubscribe, disconnect chủ động không reconnect, publish khi offline fail, close nhiều lần, clientId khác nhau và constructor Map.of("custom/+",0) nhận topic custom/data nhưng không tự subscribe sensor/*.

~~~powershell
mvn -pl java-desktop -am test
~~~

Expected: transport test pass trên socket loopback; không cần màn hình.

- [ ] **Step 5: Commit**

~~~powershell
git add pom.xml java-desktop
git commit -m "feat(java-client): add async MQTT transport"
~~~

## Task B2: Model telemetry, trạng thái và log

**Files:**
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/model/Telemetry.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/model/DeviceState.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/model/LogEntry.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/model/DashboardSnapshot.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/model/DashboardModel.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/TelemetryTest.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/DashboardModelTest.java.

**Interfaces:**
- Consumes: ConnectionState từ B1.
- Produces: Telemetry(double temp,double hum); static Optional<Telemetry> parse(byte[]).
- Produces: DeviceState enum UNKNOWN, ONLINE, OFFLINE, STALE.
- Produces: LogEntry(Instant at,String direction,String topic,String payload).
- Produces: DashboardSnapshot(ConnectionState broker,DeviceState device,Telemetry telemetry,Boolean commandedLed,List<LogEntry> logs,String error); telemetry/commandedLed null trước mẫu/lệnh đầu tiên.
- Produces: DashboardModel(Clock clock), onConnection(ConnectionState), onMessage(String,byte[]), onError(String), onSent(String,byte[]), snapshot() → DashboardSnapshot, clearLog() → void; mọi method truy cập state đều synchronized.

- [ ] **Step 1: Viết test invalid JSON và trạng thái stale**

~~~java
@Test
void rejectsMissingOutOfRangeAndNonNumericTelemetry() {
    assertTrue(Telemetry.parse("{\"temp\":28.5}".getBytes(StandardCharsets.UTF_8)).isEmpty());
    assertTrue(Telemetry.parse("{\"temp\":20,\"hum\":101}".getBytes(StandardCharsets.UTF_8)).isEmpty());
    assertTrue(Telemetry.parse("{\"temp\":\"20\",\"hum\":50}".getBytes(StandardCharsets.UTF_8)).isEmpty());
    assertEquals(new Telemetry(28.5,65.0), Telemetry.parse(
        "{\"temp\":28.5,\"hum\":65.0}".getBytes(StandardCharsets.UTF_8)).orElseThrow());
}

@Test
void preservesLastGoodSampleAfterBadJsonAndDisconnect() {
    var model = new DashboardModel(Clock.fixed(
        Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC));
    model.onConnection(ConnectionState.CONNECTED);
    model.onMessage("sensor/data",
        "{\"temp\":28.5,\"hum\":65}".getBytes(StandardCharsets.UTF_8));
    model.onMessage("sensor/data", "{".getBytes(StandardCharsets.UTF_8));
    assertEquals(28.5, model.snapshot().telemetry().temp());
    model.onConnection(ConnectionState.RECONNECTING);
    assertEquals(DeviceState.UNKNOWN, model.snapshot().device());
    assertEquals(28.5, model.snapshot().telemetry().temp());
}
~~~

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-desktop -am "-Dtest=TelemetryTest,DashboardModelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: model/parse chưa có.

- [ ] **Step 3: Parse JSON số hữu hạn và cập nhật state**

~~~java
public static Optional<Telemetry> parse(byte[] bytes) {
    try {
        var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) return Optional.empty();
        var obj = root.getAsJsonObject();
        if (!obj.has("temp") || !obj.has("hum")) return Optional.empty();
        var temp = obj.get("temp");
        var hum = obj.get("hum");
        if (!temp.isJsonPrimitive() || !hum.isJsonPrimitive()
            || !temp.getAsJsonPrimitive().isNumber()
            || !hum.getAsJsonPrimitive().isNumber()) return Optional.empty();
        double t = temp.getAsDouble(), h = hum.getAsDouble();
        if (!Double.isFinite(t) || !Double.isFinite(h)
            || t < -10 || t > 60 || h < 0 || h > 100) return Optional.empty();
        return Optional.of(new Telemetry(t,h));
    } catch (JsonParseException | IllegalStateException | NumberFormatException ex) {
        return Optional.empty();
    }
}
~~~

- [ ] **Step 3a: Tính device state và giới hạn log**

Trong DashboardModel dùng ArrayDeque<LogEntry>, mỗi append loại đầu nếu >50, payload hiển thị cắt512 ký tự. onMessage chỉ xử lý đúng ba topic; topic lạ vẫn log nhưng không đổi model. Mẫu hợp lệ cập nhật telemetry và lastSampleAt=clock.instant(), thiết bị ONLINE nếu broker CONNECTED. Trạng thái:

~~~java
Instant freshnessAt = lastSampleAt != null ? lastSampleAt : onlineSince;
DeviceState device;
if (broker != ConnectionState.CONNECTED || lastStatus == DeviceState.UNKNOWN)
    device = DeviceState.UNKNOWN;
else if (lastStatus == DeviceState.OFFLINE) device = DeviceState.OFFLINE;
else if (freshnessAt != null &&
         Duration.between(freshnessAt, clock.instant()).compareTo(Duration.ofSeconds(10)) > 0)
    device = DeviceState.STALE;
else device = lastStatus;
~~~

lastStatus bắt đầu UNKNOWN; sensor/status ONLINE/OFFLINE cập nhật giá trị tương ứng. onlineSince là Instant ghi một lần khi chuyển từ trạng thái khác sang ONLINE, không bị heartbeat ONLINE ghi đè. Khi broker mất kết nối, reset lastStatus=UNKNOWN và onlineSince=null, giữ sample/lastSampleAt để vẫn hiển thị số đo cũ. Nhận ONLINE không xóa telemetry cũ; ONLINE chưa có sample thì badge ONLINE và số đo là dấu gạch, sau10 giây thành STALE nếu vẫn chưa có sample. JSON lỗi log ERROR, giữ sample và lastSampleAt cũ. sensor/led ON/OFF chỉ là commandedLed; không xác nhận trạng thái đèn vật lý.

- [ ] **Step 4: Chạy model tests**

Thêm clock tiến thời gian qua mutable test Clock được định nghĩa ngay trong DashboardModelTest: field Instant now, getZone=UTC, withZone trả this, instant trả now, advance(Duration) cộng now. Test >10s thành STALE, keepalive ONLINE heartbeat không làm mới lastSampleAt; OFFLINE ưu tiên STALE; disconnect UNKNOWN; reconnect chờ trạng thái mới; log tối đa50; invalid status/LED không thay state; ảnh snapshot List.copyOf(logs) không mutable.

~~~powershell
mvn -pl java-desktop -am test
~~~

Expected: state/model deterministic, không chờ thời gian thật.

- [ ] **Step 5: Commit**

~~~powershell
git add java-desktop/src
git commit -m "feat(java-desktop): model telemetry and device lifecycle"
~~~

## Task B3: Swing UI và controller không chặn EDT

**Files:**
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/ui/DashboardView.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/ui/DashboardController.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/ui/SwingDashboard.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/ui/GaugePanel.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/DashboardControllerTest.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/FakeTransport.java.

**Interfaces:**
- Consumes: MqttTransport, ConnectionSettings, DashboardModel và DashboardSnapshot.
- Produces: DashboardView.render(DashboardSnapshot), showError(String).
- Produces: DashboardController(MqttTransport,DashboardModel,DashboardView,Executor uiExecutor); connect(ConnectionSettings), disconnect(), setLed(boolean), clearLog(), refresh(), close().
- Produces: SwingDashboard extends JFrame implements DashboardView; no-arg constructor, bind(DashboardController), settings() → ConnectionSettings, applySettings(ConnectionSettings) → void để điền form trên EDT.
- Produces: GaugePanel(String label,double min,double max,String unit), setValue(Double value); null vẽ dấu gạch.
- Produces test FakeTransport implements MqttTransport: fields boolean connected, Listener listener, List<String> sent; connect lưu listener, đặt connected=true, gọi onConnection(CONNECTED) và trả completedFuture; publish lưu topic+":"+UTF8 payload nếu connected, offline failedFuture; disconnect/close đặt false.

- [ ] **Step 1: Viết controller test bằng fake**

~~~java
@Test
void blocksOfflineLedAndPublishesOneCommandWhenConnected() {
    var transport = new FakeTransport();
    var renders = new ArrayList<DashboardSnapshot>();
    DashboardView view = new DashboardView() {
        public void render(DashboardSnapshot snapshot) { renders.add(snapshot); }
        public void showError(String message) {}
    };
    var model = new DashboardModel(Clock.systemUTC());
    var controller = new DashboardController(transport, model, view, Runnable::run);
    controller.setLed(true);
    assertTrue(transport.sent.isEmpty());
    controller.connect(new ConnectionSettings("127.0.0.1",1883,"user1","123456"));
    controller.setLed(true);
    assertEquals(List.of("sensor/led:ON"), transport.sent);
    assertFalse(renders.isEmpty());
    controller.close();
}
~~~

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-desktop -am "-Dtest=DashboardControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: controller/view interface chưa tồn tại.

- [ ] **Step 3: Implement controller và single UI dispatch boundary**

~~~java
public void setLed(boolean on) {
    if (!transport.isConnected()) {
        uiExecutor.execute(() -> view.showError("Broker disconnected"));
        return;
    }
    byte[] bytes = (on ? "ON" : "OFF").getBytes(StandardCharsets.UTF_8);
    transport.publish("sensor/led", bytes, 0, false).whenComplete((unused,error) -> {
        if (error != null) model.onError(error.getClass().getSimpleName());
        else model.onSent("sensor/led", bytes);
        refresh();
    });
}

public void refresh() {
    var snapshot = model.snapshot();
    uiExecutor.execute(() -> view.render(snapshot));
}
~~~

connect tạo listener cập nhật model rồi refresh cho mỗi event; initial/callback failure hiển thị một lỗi, không đổ stacktrace lên UI. onSent cập nhật commandedLed theo lệnh gửi thành công và ghi TX; incoming echo cập nhật view nhưng không gọi setLed lần nữa.

- [ ] **Step 3a: Dựng form Swing và nối controller trên EDT**

SwingDashboard dùng BorderLayout: form host/port/username/JPasswordField + Connect/Disconnect ở NORTH; hai GaugePanel và badge trạng thái ở CENTER; ON/OFF và JTable log ở SOUTH. setEnabled cho ON/OFF chỉ khi snapshot.broker==CONNECTED. Clear Log gọi controller.clearLog. form host mặc định rỗng hoặc nạp config local; không hardcode localhost cho ba máy.

~~~java
SwingUtilities.invokeLater(() -> {
    var view = new SwingDashboard();
    var controller = new DashboardController(new PahoTransport(),
        new DashboardModel(Clock.systemUTC()), view, SwingUtilities::invokeLater);
    view.bind(controller);
    view.setVisible(true);
});
~~~

- [ ] **Step 3b: Vẽ gauge và đóng timer khi thoát**

GaugePanel.paintComponent gọi super, Graphics2D anti-alias, vẽ arc240 độ; fraction clamp((value-min)/(max-min),0,1), hiển thị số gốc đã validate. Temperature range[-10,60], humidity[0,100]. JTable render từ snapshot immutable, không callback publish từ render. javax.swing.Timer mỗi1s gọi refresh để cập nhật STALE; WindowListener dừng timer và controller.close trên worker khi cửa sổ đóng.

- [ ] **Step 4: Kiểm thử controller rồi kiểm tra UI thực**

Thêm test callback mạng chỉ schedule render qua recording Executor, không render trực tiếp; error không mất sample; incoming sensor/led không publish echo; disconnect disable controls; log clear giữ telemetry; late callback sau close bị bỏ qua.

~~~powershell
mvn -pl java-desktop -am test
~~~

Manual smoke sau khi có entrypoint ở B4: mở UI Windows, resize, nhập IP sai, connect/disconnect, kiểm tra gauge/log/đơn vị không tràn. Không chạy headless JFrame test trên CI, không khẳng định UI đã kiểm tra chỉ vì controller test pass.

- [ ] **Step 5: Commit**

~~~powershell
git add java-desktop/src
git commit -m "feat(java-desktop): build Swing monitoring and LED controls"
~~~

## Task B4: CLI Java, JAR và launcher

**Files:**
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/ClientMain.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/cli/ClientOptions.java.
- Create: java-desktop/src/main/java/vn/edu/sgu/iot/cli/ClientCommands.java.
- Create: config/client.example.properties.
- Create: scripts/start_java_dashboard.bat.
- Create: scripts/check_java_connection.bat.
- Modify: java-desktop/pom.xml, .gitignore.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/ClientOptionsTest.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/ClientCommandsTest.java.
- Test: java-desktop/src/test/java/vn/edu/sgu/iot/PackagedClientTest.java.

**Interfaces:**
- Consumes: ConnectionSettings.load, PahoTransport, DashboardController/View.
- Produces: ClientOptions(String mode,Path config,String topic,String message,Path messageFile,int seconds); static parse(String[] args); byte[] payload() throws IOException đọc message hoặc file UTF8, loại BOM đầu file, giới hạn65536 byte.
- Produces: ClientCommands.run(ClientOptions options,MqttTransport transport,PrintStream out,PrintStream err) → int; không System.exit trong method này.
- Produces: ClientMain.main(String[] args), chỉ main chuyển return code thành exit; --help không mở cửa sổ hoặc kết nối mạng.
- Produces JAR: java-desktop/target/java-desktop-1.0-SNAPSHOT.jar; main class vn.edu.sgu.iot.ClientMain.

- [ ] **Step 1: Viết CLI tests và parser trước launcher**

~~~java
@Test
void parsesPublishAndRejectsUnknownOption() {
    var options = ClientOptions.parse(new String[]{"publish", "--config",
        "config/client.properties", "--topic", "sensor/led", "--message", "ON"});
    assertEquals("sensor/led", options.topic());
    assertEquals("ON", options.message());
    assertThrows(IllegalArgumentException.class,
        () -> ClientOptions.parse(new String[]{"check","--hosst","192.168.43.100"}));
}
~~~

ClientCommandsTest dùng FakeTransport và @TempDir properties file: check online exit0/in message CONNECTED; connect lỗi exit1; publish gọi đúng topic/payload và một lần; subscribe hết timeout exit0/không treo; --seconds0 hoặc >3600 rejected. Test --message-file đọc đúng bytes, bỏ BOM UTF8, file>65536 byte bị reject, --message cùng --message-file bị reject. Run test không mở Swing.

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-desktop -am "-Dtest=ClientOptionsTest,ClientCommandsTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: CLI chưa có; lỗi dependency không được tính red.

- [ ] **Step 3: Implement CLI modes và config**

Mode hợp lệ: dashboard, check, publish, subscribe. Các flag có value: --config, --topic, --message, --message-file, --seconds; unknown/duplicate/missing value là lỗi usage exit2. --config bắt buộc với CLI; dashboard có thể nhập qua UI. publish yêu cầu topic và đúng một trong --message/--message-file; subscribe mặc định sensor/# và seconds10; check timeout5s. ClientMain khởi tạo PahoTransport(Map.of(options.topic(),0)) cho mode subscribe; các mode khác dùng constructor mặc định. Không nối chuỗi thành shell command.

~~~java
var ready = transport.connect(ConnectionSettings.load(options.config()), listener)
    .toCompletableFuture();
ready.get(5, TimeUnit.SECONDS);
switch (options.mode()) {
    case "check" -> out.println("CONNECTED");
    case "publish" -> transport.publish(options.topic(), options.payload(), 0, false)
        .toCompletableFuture().get(5, TimeUnit.SECONDS);
    case "subscribe" -> done.await(options.seconds(), TimeUnit.SECONDS);
    default -> throw new IllegalArgumentException("Unknown CLI mode");
}
~~~

listener ở ClientCommands là MqttTransport.Listener, onMessage in topic + UTF8 payload chỉ trong mode subscribe, onError chỉ in loại lỗi không credential. done là CountDownLatch1, không countDown ở từng message; dùng shutdown/cancel signal để thoát sớm, hết thời gian vẫn thành công. ClientCommands đóng transport trong finally và trả0/1; main parse fail trả2. check phải hoàn tất MQTT auth+SUBACK, không chỉ mở TCP. ClientOptions.payload dùng Files.size trước Files.readString(UTF8), bỏ U+FEFF đầu chuỗi rồi encode UTF8 và kiểm tra giới hạn lần nữa.

- [ ] **Step 3a: Đóng gói CLI config và payload file**

ConnectionSettings.load đọc properties mẫu:

~~~properties
host=192.168.43.100
port=1883
username=user1
password=123456
~~~

ClientMain dashboard khởi tạo Swing trên EDT, --config nếu có điền form trước khi show nhưng không tự connect. Thêm maven-shade-plugin3.6.2 phase package, ServicesResourceTransformer, ManifestResourceTransformer Main-Class, createDependencyReducedPom=false. Chỉ include dependency runtime; kiểm tra broker classes và test classes không lọt vào shaded JAR.

- [ ] **Step 3b: Thêm launcher Windows**

~~~bat
@echo off
setlocal
cd /d "%~dp0.."
java -jar "java-desktop\target\java-desktop-1.0-SNAPSHOT.jar" dashboard %*
exit /b %ERRORLEVEL%
~~~

check_java_connection.bat dùng cùng cấu trúc, mode check và --config "config\client.properties", báo thiếu config trước khi gọi java. .gitignore thêm config/client.properties; không ignore example.

- [ ] **Step 4: Kiểm tra packaged client và UI**

PackagedClientTest thuộc profile packaged-smoke, default exclude ở Surefire giống convention mô tả trong plan A. ProcessBuilder chạy --help exit0 và check tới fixture broker exit0; sai password exit1; flags sai exit2; mọi process waitFor timeout5s/destroy finally. profile chỉ chạy sau package.

~~~powershell
mvn clean install
mvn -pl java-broker -Ppackaged-smoke "-Dtest=PackagedBrokerTest" test
mvn -pl java-desktop -Ppackaged-smoke "-Dtest=PackagedClientTest" test
java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar --help
~~~

Lệnh Maven chỉ -pl java-desktop cần broker artifact từ reactor trong local repository; mvn clean install ở trên tạo artifact đó trước khi chạy profile theo module. Bước final gate dùng mvn clean verify. Không dựa vào artifact cũ tình cờ có sẵn trong local cache.

Mở scripts/start_java_dashboard.bat và kiểm tra UI ở B3; cần quyền mở GUI theo môi trường thực thi. Expected test Maven pass và UI có bằng chứng quan sát riêng.

- [ ] **Step 5: Commit**

~~~powershell
git add java-desktop config/client.example.properties scripts/start_java_dashboard.bat scripts/check_java_connection.bat .gitignore
git commit -m "feat(java-client): package dashboard and MQTT CLI"
~~~

## Task B5: Runbook, chuyển tài liệu và nghiệm thu ba máy

**Files:**
- Create: docs/java-hotspot-runbook.md.
- Create: docs/validation/three-machine-template.md.
- Modify: README.md, PROJECT.md, TEST_INFRA.md, TEST_READY.md, HUONG_DAN_CHAY_3_MAY.md.
- Modify: docs/java-broker.md, thêm link runbook.
- Evidence sau nghiệm thu: docs/validation/YYYY-MM-DD-three-machine-results.md, dùng ngày chạy thật.

**Interfaces:**
- Consumes: broker JAR của A6; desktop JAR, check/publish/subscribe CLI của B4; firmware local config của A6.
- Produces: hướng dẫn lặp lại được và biên bản evidence; không API production mới.

- [ ] **Step 1: Ghi expected results trước nghiệm thu**

Tạo template với các cột: timestamp, commit, machine, LAN IPv4, action, expected, actual, evidence path, outcome. Mọi outcome ban đầu là NOT RUN, không điền PASS giả định. Các case bắt buộc là TCP reachability máy2/3, MQTT auth đúng/sai, DHT22 telemetry, ON/OFF, unexpected disconnect Will, broker restart reconnect/resubscribe và test client đồng thời.

- [ ] **Step 2: Viết thao tác theo từng máy và firewall giới hạn subnet**

Runbook máy1:

Nhãn máy1 ở đây nghĩa là máy broker, chọn từ một trong hai máy đã clone còn lại. Máy người dùng đã cài Wokwi tiếp tục chạy Wokwi, được gọi là máy2 trong plan. HUONG_DAN_CHAY_3_MAY.md cũ đánh số ngược hai vai trò; cập nhật mapping ở đầu guide để người vận hành không chuyển toolchain hoặc nhập nhầm IP.

~~~powershell
git pull --ff-only
git rev-parse HEAD
java -version
mvn clean install
Get-NetIPConfiguration
Copy-Item -LiteralPath config/broker.example.properties -Destination config/broker.properties
scripts/start_java_broker.bat
~~~

Copy có guard Test-Path để không ghi đè cấu hình đang có. Ghi IPv4 hotspot từ adapter thật; không tự lấy adapter đầu tiên. Nếu Windows Firewall chặn, hướng dẫn lệnh admin explicit dùng địa chỉ/subnet thực; ví dụ nhập từ người vận hành, không hardcode địa chỉ demo:

~~~powershell
$brokerLanAddress = Read-Host 'IPv4 cua may broker tren hotspot'
$hotspotSubnet = Read-Host 'CIDR subnet hotspot, vi du 192.168.43.0/24'
New-NetFirewallRule -DisplayName 'SGU Java MQTT hotspot TCP1883' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 1883 -LocalAddress $brokerLanAddress -RemoteAddress $hotspotSubnet -Profile Any
~~~

Rule chỉ subnet đã chọn; không tắt firewall hoặc mở Any remote address. Người dùng chạy thủ công nếu agent không có admin; không tự thay profile toàn bộ mạng. Nếu port thay đổi, cùng thay config và rule.

Runbook máy2:

~~~powershell
git pull --ff-only
git rev-parse HEAD
$brokerLanAddress = Read-Host 'IPv4 cua may broker tren hotspot'
Test-NetConnection -ComputerName $brokerLanAddress -Port 1883
~~~

Copy broker_config.example.h chỉ khi chưa có local config; đổi MQTT_BROKER_HOST thành IP thực; pio run -e esp32doit-devkit-v1; Start Wokwi Simulator tại workspace root. Giữ Wokwi-GUEST; không nhập tên hotspot vào firmware giả lập. Ghi serial log có Java broker connection và topic/data.

Runbook máy3:

~~~powershell
git pull --ff-only
git rev-parse HEAD
$brokerLanAddress = Read-Host 'IPv4 cua may broker tren hotspot'
Test-NetConnection -ComputerName $brokerLanAddress -Port 1883
scripts/check_java_connection.bat
scripts/start_java_dashboard.bat --config config/client.properties
~~~

Trước hai lệnh launcher, tạo config/client.properties từ example với guard và sửa host thành cùng IP thực; cài JDK21+ và mvn clean install nếu build trên máy3. Có thể copy JAR build sẵn đúng commit thay vì cài Maven. Cả ba máy phải dùng cùng commit code/config contract, không chia sẻ Wokwi key qua git.

Test-NetConnection fail từ cả máy2/3: kiểm tra bind, firewall, đúng subnet/IP và client isolation của hotspot; không giả định cùng SSID tự động liên lạc được. Test TCP pass nhưng MQTT check fail: xem auth/protocol/log broker. CLI check pass nhưng Wokwi fail: xem gateway VS Code, IP compile vào firmware, đúng binary được load trong wokwi.toml.

- [ ] **Step 3: Chạy full software gate trước buổi demo**

~~~powershell
mvn clean verify
pio run -e esp32doit-devkit-v1
git diff --check
~~~

Expected Maven unit/socket tests pass, firmware build pass. Khởi động broker local, dùng CLI publish và subscribe từ terminal khác:

~~~powershell
java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar subscribe --config config/client.properties --topic "sensor/#" --seconds 30
Set-Content -LiteralPath "$env:TEMP/sgu-telemetry.json" -Value '{"temp":28.5,"hum":65.0}' -Encoding UTF8
java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar publish --config config/client.properties --topic sensor/data --message-file "$env:TEMP/sgu-telemetry.json"
java -jar java-desktop/target/java-desktop-1.0-SNAPSHOT.jar publish --config config/client.properties --topic sensor/led --message ON
~~~

Dùng --message-file đã triển khai và test ở B4 để truyền JSON nhất quán trên Windows PowerShell5.1/PowerShell7. Java bỏ BOM UTF8 đầu file; --message và --message-file loại trừ nhau. Cửa sổ subscribe và publish là hai terminal khác nhau.

- [ ] **Step 4: Chạy nghiệm thu ba máy và lưu bằng chứng**

1. Ghi commit và IPv4 từ từng máy; máy2/3 TCP và MQTT check đều pass.
2. Máy2 chạy Wokwi; máy3 connected và ONLINE; thay DHT22 ít nhất hai bộ giá trị, quan sát trong6 giây.
3. Máy3 ON rồi OFF, quay/screenshot LED tương ứng ở máy2; log broker có client khác nhau gửi/nhận.
4. Dừng simulation đột ngột; chờ tối đa25 giây với keepalive15 và tick250ms để thấy OFFLINE. Stop có DISCONNECT thì không được coi là test unexpected disconnect; dùng ngắt mạng simulation/socket thực sự để quan sát Will.
5. Chạy lại simulation: ONLINE và telemetry phục hồi. Tắt/bật broker chủ động theo launcher, kiểm tra desktop tự reconnect và resubscribe.
6. Máy3 thêm CLI subscriber đồng thời; kiểm tra không gián đoạn UI và không nhân đôi message do namespace cũ.
7. Sai password bị từ chối; trả lại đúng config local; không đưa mật khẩu vào evidence.
8. Điền actual/evidence/outcome từng case; test chưa làm giữ NOT RUN, case fail ghi lỗi và bước tái hiện.

Đây là bước cần truy cập ba máy hoặc người vận hành gửi kết quả từ hai máy kia; clone repo không chứng minh đã chạy được. Nếu chưa có evidence, hoàn tất implementation và báo “local tests passed; three-machine acceptance pending”, không dừng sửa lỗi local có thể giải quyết.

- [ ] **Step 5: Cập nhật tài liệu và commit**

README/PROJECT chuyển luồng chính thành Java broker → Arduino ESP32 → Java Swing; bảng ports chỉ cần1883; link runbook, config example và giới hạn MQTT. Đánh dấu web_dashboard, broker/mosquitto.conf và script mosquitto_* là legacy; không xóa ngoài phạm vi.

HUONG_DAN_CHAY_3_MAY.md chuyển thành hướng dẫn Java theo đúng bảng vai trò mới và link runbook. Loại hướng dẫn cloud fallback ra khỏi luồng nghiệm thu broker tự viết. Ghi scripts/run_virtual_esp32.py là công cụ dữ liệu giả legacy, không chứng minh LED/DHT22 Arduino hoạt động và không dùng thay Wokwi trong buổi demo.

TEST_INFRA/TEST_READY mô tả Maven socket tests là gate mới; bảng chuyển scenario từ Python cũ:

| Scenario cũ | Gate mới |
|---|---|
| Auth/ports/broker | BrokerConnectionTest, BrokerConfigTest, PackagedBrokerTest |
| Topics/retained/pubsub | MqttCodecTest, TopicFilterTest, BrokerRoutingTest |
| Will/reconnect | BrokerDeliveryTest, PahoTransportTest |
| Gauges/data/LED/log | TelemetryTest, DashboardModelTest, DashboardControllerTest, UI smoke |
| CLI/hotspot | ClientCommandsTest, PackagedClientTest, three-machine results |

Không chỉnh kỳ vọng test legacy để giả lập đầy đủ tính năng mới. Nếu chạy Python historical suite và có fail vì firmware chuyển sang macro/IP LAN hoặc không còn kiến trúc Mosquitto, ghi rõ từng loại fail cùng scenario Maven thay thế; không báo tổng “all tests pass” gồm bộ cũ. Chỉ retire các assertion gắn kiến trúc cũ trong đợt migration được review; giữ test phần cứng/dữ liệu còn giá trị.

~~~powershell
git add README.md PROJECT.md TEST_INFRA.md TEST_READY.md HUONG_DAN_CHAY_3_MAY.md docs/java-broker.md docs/java-hotspot-runbook.md docs/validation/three-machine-template.md
git commit -m "docs(java-iot): document hotspot deployment and acceptance"
~~~

Chỉ stage result file nếu đã chạy thật; không stage local credential, IP config header, log chứa key, hoặc toàn bộ thư mục bằng git add . khi có thay đổi không liên quan.

## Self-review của tài liệu

Full Java PC và Arduino được bao phủ bởi B1–B4 và A6; auth/reconnect(B1), số đo/trạng thái/log(B2), EDT/gauges/LED(B3), đóng gói/CLI(B4), ba máy/firewall/evidence và tài liệu legacy(B5). Chỉ một nguồn topic chuẩn; UI không tự publish lại khi render. User chưa duyệt UI desktop cụ thể nên phạm vi này được công bố là cách hiểu trong spec; có thể điều chỉnh phần UI mà giữ nguyên broker.

## Handoff sau khi thực thi

Sau từng task chạy test liên quan, review và commit. Sau khi các gate đã có output mới, báo commit, artifact path, kết quả local và trạng thái nghiệm thu ba máy. Không dùng trạng thái “đã clone” làm bằng chứng network hoạt động.
