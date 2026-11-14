# Java MQTT Broker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo broker MQTT tự viết bằng Java, kiểm thử được bằng client độc lập và kết nối được firmware Arduino hiện có qua LAN.

**Architecture:** Module Maven java-broker dùng JDK ServerSocket và virtual threads. Codec chỉ xử lý wire protocol, session quản lý một client, router quản lý subscription/retained; I/O outbound đi qua queue riêng của mỗi session. Client Paho chỉ xuất hiện trong test để kiểm tra tương thích từ bên ngoài.

**Tech Stack:** JDK21+, Maven3.9+, JUnit Jupiter5.13.4, Eclipse Paho1.2.5 ở test scope, PlatformIO/Arduino hiện có.

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

## Bối cảnh và thứ tự thực hiện

Đọc [thiết kế](../specs/2026-11-14-java-mqtt-hotspot-design.md) trước. Người dùng hiện chỉ yêu cầu plan và commit/push tài liệu; các checkbox dưới đây dành cho đợt triển khai sau. Tạo worktree bằng skill thích hợp ở thời điểm thực thi nếu cần; không thay workspace để viết tài liệu.

Các task đi tuần tự A1 → A6. Sau A6, chuyển sang [plan desktop/hotspot](2026-11-14-java-desktop-hotspot.md). Không cần Node.js hoặc Python cho đường chạy Java. Giữ thư mục web_dashboard và hiện vật Mosquitto như legacy có nhãn rõ; không xóa để làm sạch ngoài phạm vi.

## File structure và trách nhiệm

| File | Trách nhiệm |
|---|---|
| pom.xml | Parent Maven, release21, phiên bản compiler/surefire, module java-broker |
| java-broker/pom.xml | JAR broker, JUnit/Paho test scope |
| java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/Packet.java | Gói wire gồm type, flags, body |
| java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/FrameCodec.java | Remaining Length, read/write stream |
| java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/MqttMessages.java | Record payload typed cho CONNECT/PUBLISH/SUBSCRIBE/Will |
| java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/MqttCodec.java | Parse trường MQTT và tạo response |
| java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/ProtocolException.java | IOException cho gói không hợp lệ |
| java-broker/src/main/java/vn/edu/sgu/mqtt/routing/TopicFilter.java | Kiểm tra topic/filter, wildcard matching |
| java-broker/src/main/java/vn/edu/sgu/mqtt/routing/SubscriptionRouter.java | Subscription, route và deduplicate theo client |
| java-broker/src/main/java/vn/edu/sgu/mqtt/routing/RetainedStore.java | Retained trong RAM; xóa bằng payload rỗng |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerConfig.java | Properties và validate bind/port/credentials |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientRegistry.java | Đăng ký client, thay thế trùng ClientId an toàn |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientSession.java | State machine, auth, lifecycle, queue writer |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/OutboundState.java | Packet ID, QoS1 pending và giới hạn queue |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerServer.java | Listener, accept, deadline, shutdown |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerMain.java | Entry point JAR, config, in địa chỉ LAN |
| java-broker/src/main/java/vn/edu/sgu/mqtt/server/LanAddresses.java | Liệt kê IPv4 adapter đang hoạt động |
| java-broker/src/test/java/vn/edu/sgu/mqtt/ | Unit test và Paho socket integration |
| config/broker.example.properties | Mẫu bind/port/credential demo |
| scripts/start_java_broker.bat | Khởi động JAR theo vị trí repository |
| esp32_wokwi/broker_config.example.h | Mẫu địa chỉ broker cho từng máy |
| esp32_wokwi/broker_config.h | Cấu hình cục bộ, gitignored |
| esp32_wokwi/sketch.ino | Chuyển sang broker LAN và topic chuẩn |
| docs/java-broker.md | Cách build/run/test broker, giới hạn MQTT |

Tất cả Java test nằm trong package vn.edu.sgu.mqtt, import lớp production theo đường dẫn bảng trên. Đoạn test dùng JUnit @Test, static Assertions, java.io.*, java.nio.charset.StandardCharsets và java.util.* khi cần. Không để test chỉ round-trip codec tự viết: luôn có golden bytes và Paho.

## Task A1: Framing MQTT có giới hạn và build Maven

**Files:**
- Create: pom.xml, java-broker/pom.xml.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/Packet.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/FrameCodec.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/ProtocolException.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/FrameCodecTest.java.
- Modify: .gitignore, thêm **/target/ và cấu hình local ở A6.

**Interfaces:**
- Consumes: JDK InputStream/OutputStream.
- Produces: public record Packet(int type, int flags, byte[] body).
- Produces: FrameCodec.read(InputStream in, int maxBytes) throws IOException → Packet; null khi EOF trước byte đầu tiên.
- Produces: FrameCodec.write(OutputStream out, Packet packet) throws IOException → void.
- Produces: ProtocolException extends IOException, constructor ProtocolException(String message).

- [ ] **Step 1: Khai báo Maven và test framing trước production code**

Parent POM dùng groupId vn.edu.sgu, artifactId mqtt-iot, version 1.0-SNAPSHOT, packaging pom, module java-broker. Đặt maven.compiler.release=21, project.build.sourceEncoding=UTF-8; maven-compiler-plugin3.14.1, maven-surefire-plugin3.5.4 trong build/plugins. java-broker kế thừa parent, có artifactId java-broker và hai dependency test:

~~~xml
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.13.4</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
    <scope>test</scope>
  </dependency>
</dependencies>
~~~

Golden-byte test không dùng encoder của production để dựng input:

~~~java
@Test
void readsCoalescedPacketsAndCleanEof() throws Exception {
    var input = new ByteArrayInputStream(HexFormat.of().parseHex("c000e000"));
    assertEquals(12, FrameCodec.read(input, 65536).type());
    assertEquals(14, FrameCodec.read(input, 65536).type());
    assertNull(FrameCodec.read(input, 65536));
}

@Test
void rejectsTruncatedAndOversizedLength() {
    assertThrows(EOFException.class, () -> FrameCodec.read(
        new ByteArrayInputStream(HexFormat.of().parseHex("300500")), 65536));
    assertThrows(ProtocolException.class, () -> FrameCodec.read(
        new ByteArrayInputStream(HexFormat.of().parseHex("30818004")), 65536));
}
~~~

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-broker -am "-Dtest=FrameCodecTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: compilation failure vì Packet/FrameCodec chưa tồn tại; không coi lỗi tải dependency là red hợp lệ.

- [ ] **Step 3: Viết framing tối thiểu và test chunked InputStream**

~~~java
public static Packet read(InputStream in, int maxBytes) throws IOException {
    int first = in.read();
    if (first == -1) return null;
    int length = 0;
    int multiplier = 1;
    for (int i = 0; i < 4; i++) {
        int digit = in.read();
        if (digit == -1) throw new EOFException("Truncated remaining length");
        length += (digit & 127) * multiplier;
        if (length > maxBytes) throw new ProtocolException("Packet too large");
        if ((digit & 128) == 0) {
            byte[] body = in.readNBytes(length);
            if (body.length != length) throw new EOFException("Truncated body");
            return new Packet(first >>> 4, first & 15, body);
        }
        multiplier *= 128;
    }
    throw new ProtocolException("Remaining length exceeds four bytes");
}
~~~

- [ ] **Step 3a: Viết encoder và kiểm tra golden bytes**

write ghi (type << 4)|flags, Remaining Length và body; kiểm tra type 1..14, flags 0..15, body.length<=65536 trước khi ghi; flush ở writer của session, không đóng stream. Encode Remaining Length:

~~~java
int remaining = packet.body().length;
out.write((packet.type() << 4) | packet.flags());
do {
    int digit = remaining % 128;
    remaining /= 128;
    out.write(digit | (remaining == 0 ? 0 : 128));
} while (remaining != 0);
out.write(packet.body());
~~~

Thêm test body 128 byte có length bytes 0x80 0x01, input chỉ trả một byte mỗi read, EOF giữa length, length có continuation ở byte4; kiểm tra write golden PINGRESP=d000.

- [ ] **Step 4: Chạy green và test module**

~~~powershell
mvn -pl java-broker -am test
~~~

Expected: FrameCodecTest pass, không network server nào còn sống sau suite.

- [ ] **Step 5: Commit**

~~~powershell
git add pom.xml java-broker/pom.xml java-broker/src .gitignore
git commit -m "feat(java-broker): add bounded MQTT stream framing"
~~~

## Task A2: Codec MQTT và quy tắc topic

**Files:**
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/MqttMessages.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/MqttCodec.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/routing/TopicFilter.java.
- Modify: java-broker/src/main/java/vn/edu/sgu/mqtt/protocol/ProtocolException.java, thêm connackCode và constructor hai tham số, giữ constructor một tham số.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/MqttCodecTest.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/TopicFilterTest.java.

**Interfaces:**
- Consumes: Packet, ProtocolException của A1.
- Produces: các nested record trong MqttMessages:

~~~java
public record Will(String topic, byte[] payload, int qos, boolean retain) {}
public record Connect(String clientId, String username, byte[] password,
                      boolean cleanSession, int keepAliveSeconds, Will will) {}
public record Publish(String topic, byte[] payload, int qos, boolean retain,
                      boolean dup, int packetId) {}
public record Subscription(String filter, int qos) {}
public record Subscribe(int packetId, List<Subscription> filters) {}
public record Unsubscribe(int packetId, List<String> filters) {}
~~~

- Produces: MqttCodec.decodeConnect(Packet) → Connect; decodePublish(Packet) → Publish; decodeSubscribe(Packet) → Subscribe; decodeUnsubscribe(Packet) → Unsubscribe; packetId(Packet) → int; tất cả throws ProtocolException.
- Produces: MqttCodec.connack(int code), suback(int id,List<Integer> grants), puback(int id), unsuback(int id), pingresp(), publish(Publish message) → Packet.
- Produces: TopicFilter.validateTopic(String), validateFilter(String) → void throws ProtocolException; TopicFilter.matches(String filter,String topic) → boolean.

- [ ] **Step 1: Viết golden test CONNECT/SUBSCRIBE và wildcard**

~~~java
@Test
void decodesGoldenConnect() throws Exception {
    // MQTT level4, clean session, keepalive30, ClientId=abc.
    var packet = FrameCodec.read(new ByteArrayInputStream(
        HexFormat.of().parseHex("100f00044d5154540402001e0003616263")), 65536);
    var c = MqttCodec.decodeConnect(packet);
    assertEquals("abc", c.clientId());
    assertTrue(c.cleanSession());
    assertEquals(30, c.keepAliveSeconds());
}

@Test
void matchesLevelsIncludingEmptyAndDollarTopics() throws Exception {
    assertTrue(TopicFilter.matches("sensor/+", "sensor/data"));
    assertTrue(TopicFilter.matches("sensor/#", "sensor"));
    assertTrue(TopicFilter.matches("sensor/+", "sensor/"));
    assertFalse(TopicFilter.matches("#", "$SYS/status"));
    assertThrows(ProtocolException.class,
        () -> TopicFilter.validateFilter("sensor/#/data"));
}
~~~

- [ ] **Step 2: Chạy red cho đúng hai test class**

~~~powershell
mvn -pl java-broker "-Dtest=MqttCodecTest,TopicFilterTest" test
~~~

Expected: compilation failure ở các class chưa tạo.

- [ ] **Step 3: Implement field decoding và topic matcher**

Dùng ByteBuffer big-endian. Helper private trong MqttCodec gồm u8(ByteBuffer), u16(ByteBuffer), binary(ByteBuffer), text(ByteBuffer), exhausted(ByteBuffer); bắt BufferUnderflowException thành ProtocolException. binary đọc uint16 length rồi kiểm tra remaining; password và payload là bytes, không mặc định decode như UTF-8. text dùng CharsetDecoder có REPORT:

~~~java
var decoder = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT);
String value;
try {
    value = decoder.decode(ByteBuffer.wrap(bytes)).toString();
} catch (CharacterCodingException ex) {
    throw new ProtocolException("Invalid UTF-8");
}
if (value.indexOf('\0') >= 0) throw new ProtocolException("NUL in MQTT string");
~~~

- [ ] **Step 3a: Decode CONNECT và mã lỗi kết nối**

decodeConnect kiểm tra protocol text MQTT, level4, reserved flag0, Will flags hợp lệ, password flag không đứng riêng, đủ các trường đúng thứ tự, không trailing bytes. Protocol level không hỗ trợ phải được session ánh xạ CONNACK0x01; định nghĩa ProtocolException có field int connackCode và constructor (String,int), dùng -1 cho lỗi phải đóng ngay. decode trả data trước khi auth; không chấp nhận CONNECT lần2.

- [ ] **Step 3b: Decode PUBLISH, SUBSCRIBE, UNSUBSCRIBE và fixed flags**

Fixed flags: CONNECT=0, PUBACK=0, SUBSCRIBE=2, UNSUBSCRIBE=2, PINGREQ=0, DISCONNECT=0. PUBLISH QoS=(flags>>1)&3; QoS3 bất hợp lệ, QoS0 không có packetId và DUP phải0. Packet ID ở gói có ID phải 1..65535. PINGREQ/DISCONNECT body rỗng. Kiểm tra nhiều filter, filter list không rỗng; return SUBACK mỗi filter, yêu cầu QoS2 được cấp1. PUBLISH topic phải khác rỗng.

- [ ] **Step 3c: Viết wildcard matcher và validation topic/filter**

~~~java
public static boolean matches(String filter, String topic) {
    if (topic.startsWith("$") &&
        (filter.startsWith("#") || filter.startsWith("+"))) return false;
    String[] f = filter.split("/", -1);
    String[] t = topic.split("/", -1);
    int i = 0;
    for (; i < f.length; i++) {
        if (f[i].equals("#")) return true;
        if (i >= t.length) return false;
        if (!f[i].equals("+") && !f[i].equals(t[i])) return false;
    }
    return i == t.length;
}
~~~

validateFilter reject rỗng, NUL, # không là level cuối nguyên vẹn, + không nguyên level; validateTopic reject rỗng, NUL, +/#. Mọi chuỗi encoded phải có <=65535 UTF-8 bytes.

- [ ] **Step 3d: Encode response và bổ sung golden tests**

Response golden: connack(0)=20020000; puback(7)=40020007; suback(7,List.of(0,1))=900400070001; unsuback(7)=b0020007; pingresp=d000. Thêm tests golden này, UTF-8 lỗi, truncated password/Will, ID0, flags sai, multi-filter SUBSCRIBE, UNSUBSCRIBE, QoS0/1 PUBLISH và retain.

- [ ] **Step 4: Chạy green**

~~~powershell
mvn -pl java-broker test
~~~

Expected: tất cả framing/codec/topic tests pass, bao gồm gói malformed.

- [ ] **Step 5: Commit**

~~~powershell
git add java-broker/src
git commit -m "feat(java-broker): decode MQTT messages and topic filters"
~~~

## Task A3: Listener, xác thực và lifecycle một client

**Files:**
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerConfig.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientRegistry.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientSession.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerServer.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/BrokerConnectionTest.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/RunningBroker.java.

**Interfaces:**
- Consumes: FrameCodec, MqttCodec và MqttMessages.Connect.
- Produces: public record BrokerConfig(String bindAddress,int port,String username,String password); static load(Path) throws IOException; constructor cho phép port0 để test, file config yêu cầu1..65535.
- Produces: BrokerServer(BrokerConfig), start() throws IOException, int port(), close() implements AutoCloseable.
- Produces: ClientSession implements Runnable, AutoCloseable: String clientId(), boolean enqueue(Packet), void sendPublish(Publish), void close(CloseReason), void close().
- Produces: nested enum ClientSession.CloseReason { GRACEFUL, NETWORK, PROTOCOL, KEEPALIVE, REPLACED, SHUTDOWN, OVERLOAD }.
- Produces: ClientRegistry.register(String id,ClientSession replacement), remove(String id,ClientSession expected), snapshot() → List<ClientSession>; mọi thao tác state atomic.
- Produces test fixture RunningBroker.open() throws IOException, server(), uri(), close(); mở loopback port0, close shutdown và await worker termination tối đa3s.

- [ ] **Step 1: Viết test client Paho bên ngoài broker**

~~~java
@Test
void acceptsValidCredentialsAndRejectsWrongPassword() throws Exception {
    try (var running = RunningBroker.open();
         var good = new MqttClient(running.uri(), "good", new MemoryPersistence());
         var bad = new MqttClient(running.uri(), "bad", new MemoryPersistence())) {
        var options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setUserName("user1");
        options.setPassword("123456".toCharArray());
        good.connect(options);
        assertTrue(good.isConnected());
        good.disconnect();
        options.setPassword("wrong".toCharArray());
        var error = assertThrows(MqttException.class, () -> bad.connect(options));
        assertEquals(4, error.getReasonCode());
    }
}
~~~

RunningBroker implement bằng new BrokerServer(new BrokerConfig("127.0.0.1",0,"user1","123456")); start trước khi trả fixture; uri trả tcp://127.0.0.1:port. Dùng imports Paho org.eclipse.paho.client.mqttv3.* và persist.MemoryPersistence.

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-broker "-Dtest=BrokerConnectionTest" test
~~~

Expected: server chưa tồn tại hoặc chưa trả CONNACK; timeout hữu hạn ở test client.

- [ ] **Step 3: Implement accept loop và state machine**

~~~java
serverSocket.bind(new InetSocketAddress(config.bindAddress(), config.port()));
// Chỉ báo start thành công sau bind; lưu localPort trước accept worker.
Thread acceptor = Thread.startVirtualThread(() -> {
    while (!serverSocket.isClosed()) {
        try {
            Socket socket = serverSocket.accept();
            socket.setTcpNoDelay(true);
            // Chuyển vào ClientSession; dùng Semaphore32, từ chối khi hết permit.
            acceptConnection(socket);
        } catch (IOException ex) {
            if (!serverSocket.isClosed()) recordAcceptFailure(ex);
        }
    }
});
~~~

Định nghĩa acceptConnection(Socket) và recordAcceptFailure(IOException) là private methods trong BrokerServer: method đầu lấy permit rồi khởi tạo/session task; method sau log warning và close listener nếu lỗi accept lặp3 lần. Không dùng busy loop. Test connection limit phải tính cả socket chưa auth; permit trả đúng một lần trong cleanup.

- [ ] **Step 3a: Authenticate và ghi CONNACK**

ClientSession constructor (Socket,BrokerConfig,ClientRegistry,ScheduledExecutorService) ở task này; A4 thêm router dependency. Luồng:

~~~java
// Sau read CONNECT và validate flags/protocol:
if (!connect.cleanSession() || connect.clientId().isEmpty()) {
    writeAndClose(MqttCodec.connack(2));
    return;
}
if (connect.will() != null && connect.will().qos() == 2) {
    writeAndClose(MqttCodec.connack(3));
    return;
}
if (!credentialsMatch(connect)) {
    writeAndClose(MqttCodec.connack(4));
    return;
}
registry.register(connect.clientId(), this);
enqueue(MqttCodec.connack(0));
~~~

Private methods writeAndClose(Packet) ghi response rồi flush trước khi đóng; credentialsMatch(Connect) so user và password bytes UTF-8 của config bằng MessageDigest.isEqual. Không đưa password vào toString/log. Đăng ký sẽ đóng client cũ REPLACED bên ngoài registry lock; registry.remove(id,this) dùng remove(key,value) để cleanup cũ không xóa client mới.

- [ ] **Step 3b: Quản lý registry, state và deadline**

Trạng thái NEW → CONNECTED → CLOSED; NEW chỉ nhận CONNECT, CONNECTED chỉ xử lý gói hợp lệ. PINGREQ → PINGRESP, DISCONNECT → GRACEFUL; gói malformed đóng PROTOCOL. Tick deadline mỗi250ms dựa System.nanoTime; CONNECT deadline10s. Frame đọc sau byte đầu tiên phải hoàn thành trong10s: wrapper InputStream theo absolute deadline, chỉnh socket SO_TIMEOUT theo phần thời gian còn lại; không reset deadline ở mỗi byte. Sau gói đầy đủ, cập nhật lastPacketNanos. Keepalive0 cho phép idle; vẫn có deadline cho gói dở dang.

- [ ] **Step 3c: Tuần tự hóa writer và cleanup resources**

Session ban đầu dùng queue256 và một writer virtual thread cho toàn bộ gói outbound; enqueue=false đóng OVERLOAD, không write vào socket trong callback/router. close idempotent bằng AtomicBoolean, đóng socket và cancel timer, trả permit callback; BrokerServer.close đóng tất cả socket, listener và scheduler, join workers hữu hạn.

- [ ] **Step 4: Thêm kiểm thử lifecycle và chạy green**

Test auth thiếu/sai, level5, ClientId rỗng, cleanSession=false, gói trước CONNECT, CONNECT lần2, duplicate ClientId, graceful disconnect, initial deadline và slow partial-frame bằng clock/deadline injection có package-private constructor ở ClientSession. Chỉ socket integration dùng thời gian thật, deadline rút ngắn qua constructor test; không chờ10s cho mỗi unit test.

~~~powershell
mvn -pl java-broker test
~~~

Expected: Paho connect/disconnect/ping pass, không có worker/socket leak. Mọi assertion chờ mạng dùng CountDownLatch/BlockingQueue timeout3s, không Thread.sleep để đoán.

- [ ] **Step 5: Commit**

~~~powershell
git add java-broker/src
git commit -m "feat(java-broker): accept authenticated MQTT sessions"
~~~

## Task A4: Publish/subscribe, retained và unsubscribe

**Files:**
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/routing/SubscriptionRouter.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/routing/RetainedStore.java.
- Modify: java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientSession.java.
- Modify: java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerServer.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/BrokerRoutingTest.java.

**Interfaces:**
- Consumes: ClientSession.enqueue(Packet), sendPublish(Publish), MqttCodec and TopicFilter.
- Produces: RetainedStore.put(Publish), matching(List<Subscription>) → List<Publish>; copies payload arrays, empty retained payload deletes.
- Produces: SubscriptionRouter(RetainedStore); subscribe(ClientSession,Subscribe), unsubscribe(ClientSession,Unsubscribe), remove(ClientSession), route(Publish).
- Session constructor thêm SubscriptionRouter cuối danh sách; BrokerServer tạo một router chung và truyền cho mọi session.

- [ ] **Step 1: Viết socket test route sang client khác**

~~~java
@Test
void routesWildcardTelemetryAndStopsAfterUnsubscribe() throws Exception {
    try (var running = RunningBroker.open();
         var sub = new MqttClient(running.uri(), "sub", new MemoryPersistence());
         var pub = new MqttClient(running.uri(), "pub", new MemoryPersistence())) {
        var received = new LinkedBlockingQueue<String>();
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName("user1");
        options.setPassword("123456".toCharArray());
        sub.connect(options);
        pub.connect(options);
        sub.subscribe("sensor/+", 0,
            (topic, msg) -> received.add(topic + ":" +
                new String(msg.getPayload(), StandardCharsets.UTF_8)));
        pub.publish("sensor/data", "{\"temp\":28.5,\"hum\":65.0}".getBytes(
            StandardCharsets.UTF_8), 0, false);
        assertEquals("sensor/data:{\"temp\":28.5,\"hum\":65.0}",
            received.poll(3, TimeUnit.SECONDS));
        sub.unsubscribe("sensor/+");
        pub.publish("sensor/data", new byte[]{1}, 0, false);
        assertNull(received.poll(250, TimeUnit.MILLISECONDS));
        pub.disconnect();
        sub.disconnect();
    }
}
~~~

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-broker "-Dtest=BrokerRoutingTest" test
~~~

Expected: subscriber chưa nhận SUBACK hoặc không nhận publication.

- [ ] **Step 3: Implement router và retained atomically**

Subscription map theo identity ClientSession, mỗi client map filter→grantedQos. SUBSCRIBE update filter thay vì thêm duplicate; packetID của request chỉ dùng SUBACK, không dùng outbound publish ID. Router lock chỉ tính state và enqueue; tuyệt đối không flush socket dưới lock.

~~~java
// Trong route, lấy snapshot target + effectiveQos dưới lock.
int effectiveQos = Math.min(message.qos(), highestMatchingSubscriptionQos);
target.sendPublish(new Publish(message.topic(), message.payload().clone(),
    effectiveQos, false, false, 0));
~~~

highestMatchingSubscriptionQos là biến local tính max của các filter khớp; một target chỉ xuất hiện một lần trong snapshot. A4 có deliverable QoS0, SUBACK cấp0; A5 nâng phần cấp QoS và outbound ID lên QoS1.

- [ ] **Step 3a: Lưu/replay retained và UNSUBACK**

Để A4 có deliverable đúng: SUBACK chỉ cấp0 trong task này, ghi rõ bước A5 nâng lên1. subscribe enqueue SUBACK trước, đăng ký filter, rồi replay retained matching với retain=true; giữ một message/topic nếu nhiều filter khớp. route retained publish gọi RetainedStore.put trước phát live; live nhận retain=false; replay có QoS=min(storedQos,grantedQos). unsubscribe enqueue UNSUBACK và remove các filter trước khi xử lý publication tiếp theo.

Session switch type3 decodePublish, reject QoS2, route; type8 decodeSubscribe; type10 decodeUnsubscribe. QoS1 inbound PUBACK do A5; A4 chỉ hỗ trợ QoS0 rõ ràng.

- [ ] **Step 4: Chạy retained và routing tests**

Thêm test retained gửi trước subscribe; replay retain=true; payload rỗng xóa; late subscriber không nhận sau xóa; overlapping filter không nhân đôi; $SYS không khớp #; disconnected client bị gỡ; unsubscribe một filter vẫn giữ filter khác.

~~~powershell
mvn -pl java-broker test
~~~

Expected: Paho QoS0 pub/sub, retained và unsubscribe pass.

- [ ] **Step 5: Commit**

~~~powershell
git add java-broker/src
git commit -m "feat(java-broker): route subscriptions and retained messages"
~~~

## Task A5: QoS1, Will và chống client chậm

**Files:**
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/OutboundState.java.
- Modify: java-broker/src/main/java/vn/edu/sgu/mqtt/server/ClientSession.java.
- Modify: java-broker/src/main/java/vn/edu/sgu/mqtt/routing/SubscriptionRouter.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/BrokerDeliveryTest.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/OutboundStateTest.java.

**Interfaces:**
- Consumes: Publish/Will, lifecycle CloseReason và router.route(Publish).
- Produces: OutboundState(int maxPending), int reserve(Publish), void acknowledge(int packetId), int pendingCount(), void clear().
- reserve trả ID1..65535, throw IllegalStateException nếu pending>=128; thao tác thread-safe, không tái dùng ID chưa ACK.
- ClientSession.sendPublish giữ interface A3; QoS1 reserve ID, enqueue encoded packet, rollback reserve khi enqueue thất bại.

- [ ] **Step 1: Viết test QoS1 và lost client Will**

~~~java
@Test
void holdsDistinctPacketIdsUntilAcknowledged() {
    var state = new OutboundState(128);
    var message = new Publish("sensor/status", "ONLINE".getBytes(
        StandardCharsets.UTF_8), 1, true, false, 0);
    int first = state.reserve(message);
    int second = state.reserve(message);
    assertNotEquals(first, second);
    assertEquals(2, state.pendingCount());
    state.acknowledge(first);
    assertEquals(1, state.pendingCount());
}

@Test
void publishesRetainedWillAfterAbruptDisconnect() throws Exception {
    try (var running = RunningBroker.open();
         var observer = new MqttClient(running.uri(), "observer", new MemoryPersistence());
         var edge = new MqttClient(running.uri(), "edge", new MemoryPersistence())) {
        var statuses = new LinkedBlockingQueue<String>();
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName("user1");
        options.setPassword("123456".toCharArray());
        observer.connect(options);
        observer.subscribe("sensor/status", 1, (topic, msg) ->
            statuses.add(new String(msg.getPayload(), StandardCharsets.UTF_8)));
        options.setWill("sensor/status", "OFFLINE".getBytes(StandardCharsets.UTF_8), 1, true);
        edge.connect(options);
        edge.disconnectForcibly(0, 0, false);
        assertEquals("OFFLINE", statuses.poll(3, TimeUnit.SECONDS));
        observer.disconnect();
    }
}
~~~

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-broker "-Dtest=BrokerDeliveryTest,OutboundStateTest" test
~~~

Expected: OutboundState chưa có hoặc Will/QoS1 chưa được gửi.

- [ ] **Step 3: Implement ID allocation, PUBACK và Will-once**

~~~java
public synchronized int reserve(Publish message) {
    if (pending.size() >= maxPending) throw new IllegalStateException("QoS1 pending full");
    for (int attempts = 0; attempts < 65535; attempts++) {
        nextId = nextId % 65535 + 1;
        if (!pending.containsKey(nextId)) {
            pending.put(nextId, message);
            return nextId;
        }
    }
    throw new IllegalStateException("Packet IDs exhausted");
}
~~~

- [ ] **Step 3a: Ghép QoS1 ACK vào session và nâng SUBACK grants**

pending là Map<Integer,Publish>, nextId bắt đầu0, maxPending là field constructor. acknowledge remove ID; ACK cho ID không còn pending được bỏ qua, không route lại. QoS1 inbound enqueue PUBACK(packetId) sau khi router nhận ownership; outbound QoS1 chỉ clear khi PUBACK. Không implement retry timer trong live connection hay session persistence. Nâng SUBACK grants thành min(requestedQos,1).

- [ ] **Step 3b: Gửi Will đúng một lần khi connection kết thúc bất thường**

~~~java
// Trong cleanup được bảo vệ bởi AtomicBoolean.compareAndSet(false,true):
registry.remove(clientId, this);
router.remove(this);
if (accepted && will != null && reason != CloseReason.GRACEFUL) {
    router.route(new Publish(will.topic(), will.payload().clone(),
        will.qos(), will.retain(), false, 0));
}
outboundState.clear();
~~~

accepted chỉ true sau auth, đăng ký và CONNACK chấp nhận. CloseReason.SHUTDOWN vẫn best-effort Will cho các observer còn mở; không cam kết retained sau restart. Chặn publication sau close; captured Will phát đúng một lần kể cả writer lỗi cùng lúc timer.

- [ ] **Step 3c: Chặn slow client và kiểm tra race khi thay ClientId**

Giữ socket writes một writer và queue256; pending128/queue256 vượt ngưỡng đóng OVERLOAD. Với ClientId replacement, đóng cũ trước khi cho new session publish ONLINE; close old cleanup không gỡ new.

- [ ] **Step 4: Thêm delivery tests và chạy green**

Kiểm tra QoS0 pub tới subscription1 vẫn nhận QoS0; QoS1 pub nhận PUBACK; outbound ACK xóa pending; duplicate inbound QoS1 được phép nhận lặp theo at-least-once; graceful DISCONNECT không Will; keepalive timeout phát Will; client auth fail không Will; duplicate ClientId chỉ một Will; slow observer không chặn observer khác; queue overflow cleanup hữu hạn. Dùng socket raw cho kiểm thử cố ý không ACK vì Paho tự ACK.

~~~powershell
mvn -pl java-broker test
~~~

Expected: tests delivery/lifecycle/routing pass và quá trình Maven kết thúc bình thường.

- [ ] **Step 5: Commit**

~~~powershell
git add java-broker/src
git commit -m "feat(java-broker): support QoS1 will and bounded delivery"
~~~

## Task A6: Đóng gói broker và trỏ Arduino sang LAN

**Files:**
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/BrokerMain.java.
- Create: java-broker/src/main/java/vn/edu/sgu/mqtt/server/LanAddresses.java.
- Create: config/broker.example.properties.
- Create: scripts/start_java_broker.bat.
- Create: esp32_wokwi/broker_config.example.h.
- Create: docs/java-broker.md.
- Modify: java-broker/pom.xml, .gitignore.
- Modify: esp32_wokwi/sketch.ino:12-20,55-73,143-148.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/BrokerConfigTest.java.
- Test: java-broker/src/test/java/vn/edu/sgu/mqtt/PackagedBrokerTest.java.

**Interfaces:**
- Consumes: BrokerServer.start/port/close, BrokerConfig.load.
- Produces: BrokerMain.main(String[] args); usage --config <path> hoặc --help; config file bắt buộc.
- Produces: LanAddresses.ipv4() → List<String>, adapter active, non-loopback; in nhiều ứng viên, không tự chọn Docker/VPN adapter.
- Produces JAR: java-broker/target/java-broker-1.0-SNAPSHOT.jar.
- Produces Arduino macros: MQTT_BROKER_HOST, MQTT_BROKER_PORT, MQTT_USERNAME, MQTT_PASSWORD.

- [ ] **Step 1: Viết test config và process JAR**

~~~java
@Test
void readsExternalConfigAndRejectsInvalidPort(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("broker.properties");
    Files.writeString(file, "bind.address=0.0.0.0\nport=1883\nusername=user1\npassword=123456\n");
    assertEquals("0.0.0.0", BrokerConfig.load(file).bindAddress());
    Files.writeString(file, "bind.address=0.0.0.0\nport=70000\nusername=user1\npassword=123456\n");
    assertThrows(IllegalArgumentException.class, () -> BrokerConfig.load(file));
}
~~~

PackagedBrokerTest chạy chỉ ở profile packaged-smoke, sau package: ProcessBuilder(javaExecutable,"-jar",absoluteJar,"--help"), waitFor(3,SECONDS), exit0, output chứa --config và không chứa password. Thêm Paho runtime launch test với config port riêng khả dụng, retry connect deadline3s, process.destroy trong finally. Định nghĩa javaExecutable từ java.home/bin/java.exe trên Windows hoặc java trên OS khác; absoluteJar từ basedir/target/java-broker-1.0-SNAPSHOT.jar. Cấu hình Surefire default exclude PackagedBrokerTest, profile packaged-smoke include lại; không chạy integration package test trước JAR tồn tại.

- [ ] **Step 2: Chạy red**

~~~powershell
mvn -pl java-broker "-Dtest=BrokerConfigTest" test
~~~

Expected: properties loading/validation chưa đạt.

- [ ] **Step 3: Implement launcher/config và firmware config**

Properties mẫu:

~~~properties
bind.address=0.0.0.0
port=1883
username=user1
password=123456
~~~

Reject missing/empty key, port không phải số hoặc ngoài1..65535, bind không resolve được; main lỗi trả exit1, --help trả0. Shutdown hook gọi broker.close. Broker log startup gồm bind/port, số client, IPv4 gợi ý; không log object chứa config.password. Configure maven-jar-plugin3.4.2 Manifest Main-Class=vn.edu.sgu.mqtt.server.BrokerMain; broker JAR chỉ có classes JDK.

~~~bat
@echo off
setlocal
cd /d "%~dp0.."
if not exist "config\broker.properties" (
  echo Copy config\broker.example.properties to config\broker.properties first.
  exit /b 1
)
java -jar "java-broker\target\java-broker-1.0-SNAPSHOT.jar" --config "config\broker.properties"
exit /b %ERRORLEVEL%
~~~

- [ ] **Step 3a: Chuyển Arduino sang local broker config**

Firmware include có lỗi build rõ ràng nếu chưa cấu hình, không fallback cloud:

~~~cpp
#if __has_include("broker_config.h")
#include "broker_config.h"
#else
#error "Copy broker_config.example.h to broker_config.h and set broker LAN IPv4"
#endif

const char* mqtt_server = MQTT_BROKER_HOST;
const int mqtt_port = MQTT_BROKER_PORT;
const char* mqtt_user = MQTT_USERNAME;
const char* mqtt_pass = MQTT_PASSWORD;
const char* topic_data = "sensor/data";
const char* topic_led = "sensor/led";
const char* topic_status = "sensor/status";
~~~

broker_config.example.h:

~~~cpp
#pragma once
// Example only: replace with the IPv4 shown on broker machine's hotspot adapter.
#define MQTT_BROKER_HOST "192.168.43.100"
#define MQTT_BROKER_PORT 1883
#define MQTT_USERNAME "user1"
#define MQTT_PASSWORD "123456"
~~~

- [ ] **Step 3b: Chuẩn hóa topic và telemetry Arduino**

Sửa reconnect chỉ subscribe topic_led một lần, publish ONLINE một lần, giữ Will OFFLINE QoS1 retain; loop publish mỗi topic một lần. Đổi log HiveMQ thành Java MQTT broker. Giữ Wokwi-GUEST, GPIO và JSON; làm JSON số thật:

~~~cpp
doc["temp"] = data.temperature;
doc["hum"] = data.humidity;
client.publish(topic_data, buffer);
client.publish(topic_status, "ONLINE", true);
~~~

Giữ serializer sau khi gán số và trước publish. Đặt client.setKeepAlive(15), client.setSocketTimeout(5), kiểm tra return của publish/subscribe và log lỗi; firmware publish bình thường ở QoS0 do PubSubClient. Thêm .gitignore config/broker.properties, esp32_wokwi/broker_config.h, **/target/; example files vẫn tracked.

- [ ] **Step 4: Build/test artifact và Arduino**

~~~powershell
mvn -pl java-broker clean verify
mvn -pl java-broker -Ppackaged-smoke "-Dtest=PackagedBrokerTest" test
Copy-Item -LiteralPath config/broker.example.properties -Destination config/broker.properties
Copy-Item -LiteralPath esp32_wokwi/broker_config.example.h -Destination esp32_wokwi/broker_config.h
pio run -e esp32doit-devkit-v1
~~~

Copy chỉ khi local file chưa tồn tại; không ghi đè IP/credential người dùng. Nếu pio chưa trên PATH dùng pio.exe trong PlatformIO Python env đã cài, không đổi toolchain không cần thiết. IP mẫu đủ compile; phải đổi sang IP thực để chạy. Expected Maven unit/integration và firmware build pass; packaged --help không phụ thuộc cwd. Thử cổng bị chiếm: broker exit1, không stop process đang giữ cổng.

- [ ] **Step 5: Viết guide và commit**

docs/java-broker.md ghi nguyên các lệnh build/run trên, phạm vi MQTT hỗ trợ, tài khoản demo, cách chọn IPv4 hotspot, cách đổi port, không disk persistence/QoS2/WebSocket, và phân biệt broker Java với hiện vật legacy. Ghi trạng thái chạy Wokwi thực tế nếu đã quan sát; không ghi “ba máy pass” ở task này.

~~~powershell
git add java-broker config/broker.example.properties scripts/start_java_broker.bat esp32_wokwi/sketch.ino esp32_wokwi/broker_config.example.h .gitignore docs/java-broker.md
git commit -m "feat(java-broker): package LAN broker and configure Arduino client"
~~~

## Kiểm tra kết thúc dự án con A

- [ ] A1 framing bounded/chunked, A2 protocol/topic malformed, A3 auth/lifecycle, A4 pubsub/retain, A5 QoS1/Will/slow client, A6 packaged process + Arduino build đều có evidence.
- [ ] Chạy mvn -pl java-broker dependency:tree, xác nhận Paho test scope và không có broker runtime dependency.
- [ ] Mọi socket/thread/scheduler đóng trong finally/AutoCloseable, test suite không treo.
- [ ] Không lấy tổng pass của Python legacy làm bằng chứng MQTT Java; không xóa test để che fail.
- [ ] Chuyển dự án con B; chưa tuyên bố nghiệm thu hotspot.

## Self-review của tài liệu

Đã đối chiếu ràng buộc với A1–A6: framing/UTF-8/topic(A1,A2), auth/duplicate/deadline(A3), routing/retain(A4), QoS1/Will/backpressure(A5), LAN/config/firmware/package(A6). Các giới hạn MQTT giữ đúng spec; phạm vi clean-session được công bố thay vì giả hỗ trợ persistent session. Interface test fixture và production được định nghĩa trước nơi dùng. Những lệnh build/test trong tài liệu là kế hoạch, chưa được chạy cho code chưa tồn tại.
