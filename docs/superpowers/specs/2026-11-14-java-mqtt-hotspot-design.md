# Thiết kế MQTT Java và Arduino trên ba máy cùng hotspot

Ngày: 2026-11-14. Tài liệu này ghi lại phạm vi để lập kế hoạch theo yêu cầu trực tiếp của người dùng; chưa có mã triển khai hay kết quả kiểm thử hệ thống mới.

## Mục tiêu và cách hiểu phạm vi

Thay Mosquitto bằng MQTT broker tự viết Java. Ba máy đã clone cùng repository và kết nối cùng hotspot. Máy 2 có Wokwi trong VS Code, đã nhập key và có ESP32, LED, cảm biến DHT22. Arduino là một yêu cầu được người dùng xác nhận.

Yêu cầu “full Java” được hiểu trong kế hoạch này là broker và ứng dụng PC dùng Java; firmware ESP32 dùng Arduino/C++. Người dùng chưa chọn rõ giữa giữ dashboard web và chuyển desktop; kế hoạch chọn desktop Swing để đáp ứng cách hiểu đầy đủ của yêu cầu Java. Đây là giả định phạm vi được nêu công khai, có thể sửa riêng kế hoạch desktop nếu yêu cầu môn học giữ giao diện web.

Người dùng yêu cầu lập plan, sau đó commit và push lên GitHub. Chỉ tài liệu được thay đổi trong đợt công việc này. Việc yêu cầu plan được coi là chỉ dẫn chuyển sang lập kế hoạch; tài liệu không khẳng định thiết kế Java chi tiết đã được người dùng duyệt từng phần.

## Hiện trạng đã kiểm tra

- Broker cũ: broker/mosquitto.conf, broker/password.txt và broker/start_broker.bat.
- Firmware: esp32_wokwi/sketch.ino, DHTPIN=16, LEDPIN=4, chu kỳ gửi 3000 ms.
- Firmware đang dùng broker.hivemq.com:1883, vừa gửi topic sensor/* vừa gửi thuantran260/sensor/*.
- Dashboard web mặc định bật USE_PUBLIC_BROKER và kết nối HiveMQ.
- Các script CLI dùng mosquitto_pub/sub, host localhost.
- Khi đồng bộ origin/main trước push, đã nhận commit 24bc7e0 thêm HUONG_DAN_CHAY_3_MAY.md và scripts/run_virtual_esp32.py. Guide đó mô tả Mosquitto/web; script Python gửi dữ liệu giả qua HiveMQ. Plan B sẽ cập nhật guide; script dữ liệu giả không thay cho Arduino/Wokwi trong nghiệm thu.
- tests/test_tier*.py và tests/test_helpers.py kiểm tra nhiều tên file/cấu hình Mosquitto và web; không thể lấy các kiểm tra này làm bằng chứng broker Java hoạt động.
- Máy làm việc hiện có JDK/javac 24.0.2 và Maven 3.9.12. Chưa kiểm tra công cụ trên hai máy còn lại.
- Chưa quan sát khả năng liên lạc thực tế giữa ba máy trên hotspot.

## Ràng buộc dùng chung

Các dòng sau được sao chép nguyên văn vào hai implementation plan:

- Phần mềm PC dùng Java 21 trở lên; Maven compiler release=21; firmware ESP32 dùng Arduino/C++.
- Broker MQTT tự viết bằng Java chuẩn; không nhúng Mosquitto, HiveMQ, Moquette hoặc broker có sẵn.
- Eclipse Paho 1.2.5 chỉ dùng làm client Java và client kiểm thử; không xử lý broker.
- Ba máy cùng hotspot; client kết nối IPv4 LAN của máy broker; bind mặc định của broker là 0.0.0.0:1883.
- Topic chuẩn là sensor/data, sensor/led, sensor/status; bỏ publish/subscribe trùng namespace public khỏi luồng chạy mới.
- DHT22 dùng GPIO16, LED dùng GPIO4, telemetry mỗi 3000 ms; giữ sơ đồ phần cứng hiện tại.
- Tài khoản demo là user1/123456, cấu hình được; không log mật khẩu hoặc commit key Wokwi.
- Kiểm thử tự động một máy và nghiệm thu thực tế ba máy được báo cáo riêng; chỉ xác nhận ba máy sau khi có bằng chứng từ cả ba.

## Phân chia hai dự án con

### A. Broker Java và kết nối firmware

Một module Maven java-broker không có thư viện MQTT lúc chạy. Broker sở hữu TCP framing, codec MQTT, xác thực, registry client, subscription routing, retained messages và lifecycle. Dùng ServerSocket, Socket, virtual threads và ScheduledExecutorService của JDK.

Kết quả độc lập: executable JAR chạy được, kiểm thử bằng client Paho qua TCP, firmware trỏ tới broker LAN. Có thể kiểm thử broker trước khi desktop hoàn thành.

Plan: [Java MQTT broker](../plans/2026-11-14-java-mqtt-broker.md).

### B. Ứng dụng Java và nghiệm thu hotspot

Module Maven java-desktop dùng Swing cho hiển thị và Paho cho kết nối MQTT. Có chế độ CLI Java để publish/subscribe, kiểm tra kết nối và giả lập telemetry. Broker vẫn tự viết theo dự án A.

Kết quả độc lập: desktop kiểm thử được bằng transport giả và broker test; khi ghép với A, chạy được demo ba máy. Swing được chọn vì có trong JDK; JavaFX cần thêm runtime/packaging, còn giữ web làm giao diện chính không đáp ứng cách hiểu “full Java” đã chọn.

Plan: [Java desktop và hotspot](../plans/2026-11-14-java-desktop-hotspot.md).

## Topology và hợp đồng dữ liệu

| Máy | Thành phần | Địa chỉ |
|---|---|---|
| 1 | Broker Java | Bind 0.0.0.0:1883, in danh sách IPv4 để chọn đúng adapter hotspot |
| 2 | Wokwi VS Code chạy Arduino ESP32 | Wokwi-GUEST; mqtt_server là IPv4 của máy 1 |
| 3 | Java Swing dashboard hoặc CLI | tcp://IPv4-máy-1:1883 |

Số máy trong hai plan là nhãn theo vai trò. Máy người dùng đã cài Wokwi tiếp tục giữ Wokwi và được gọi là máy2 trong plan; chọn một trong hai máy đã clone còn lại làm broker/máy1. Guide cũ gọi máy Wokwi là máy1 và broker là máy2; khi cập nhật guide phải đổi nhãn hoặc giải thích mapping rõ ràng, không chuyển toolchain Wokwi sang máy khác.

0.0.0.0 là địa chỉ bind, không phải địa chỉ nhập ở client. localhost/host.wokwi trên máy 2 không đại diện máy 1. Gateway tích hợp Wokwi VS Code nối mạng ESP32 giả lập với LAN của máy 2; đường từ LAN máy 2 sang máy 1 phải được kiểm tra.

| Topic | Publisher | Subscriber | Payload / QoS |
|---|---|---|---|
| sensor/data | ESP32 hoặc Java CLI | Desktop/CLI | {"temp":28.5,"hum":65.0}; QoS0; không retain |
| sensor/led | Desktop/CLI | ESP32 và desktop | ON/OFF; QoS0; không retain |
| sensor/status | ESP32; broker gửi Will | Desktop/CLI | ONLINE/OFFLINE; retained; Will QoS1 |

Desktop phản ánh lệnh LED trên sensor/led, không gọi đó là trạng thái phần cứng đã xác nhận. Chưa thêm topic ACK mới. Telemetry hợp lệ yêu cầu đủ hai số hữu hạn temp/hum, temp thuộc [-10,60], hum thuộc [0,100]. JSON lỗi không làm mất mẫu hợp lệ trước đó.

## Phạm vi MQTT phiên bản đầu

Broker phục vụ MQTT 3.1.1, CleanSession=1, QoS0/1, CONNECT/CONNACK, PUBLISH/PUBACK, SUBSCRIBE/SUBACK, UNSUBSCRIBE/UNSUBACK, PINGREQ/PINGRESP và DISCONNECT. Giới hạn này đáp ứng các client trong dự án; không quảng bá là broker tuân thủ toàn bộ MQTT 3.1.1.

- Từ chối protocol level khác 4 bằng CONNACK 0x01.
- ClientId rỗng hoặc CleanSession=0 bị từ chối bằng CONNACK 0x02; đây là giới hạn sản phẩm được công bố, không hỗ trợ persistent session.
- Sai tài khoản trả CONNACK 0x04; CONNECT chỉ xuất hiện một lần và phải là gói đầu tiên.
- Will QoS2 bị từ chối kết nối bằng CONNACK 0x03; PUBLISH QoS2 đóng kết nối. SUBSCRIBE xin QoS2 có thể được cấp QoS1.
- Wildcard + chiếm nguyên một level; # là level cuối; filter bắt đầu wildcard không khớp topic bắt đầu $; publish topic không chứa wildcard hoặc NUL.
- MQTT strings phải là UTF-8 hợp lệ, không NUL, không unpaired surrogate; không dùng Java modified UTF-8.
- Retained payload rỗng xóa retained record; retained tồn tại trong RAM qua disconnect của client, mất khi broker khởi động lại.
- QoS outbound=min(QoS publish, QoS cao nhất của subscription khớp); mỗi client nhận một bản khi nhiều filter trùng nhau.
- SUBACK được ghi trước khi replay retained. QoS1 outbound có packet ID riêng, chờ PUBACK, giải phóng đúng ID; không tái dùng ID đang chờ.
- Trong cùng TCP connection, MQTT 3.1.1 không yêu cầu retry theo timer; pending QoS1 được giữ tới ACK hoặc đóng connection. Clean session không replay pending qua reconnect.
- Will gửi một lần khi EOF/mất mạng/timeout hoặc thay thế ClientId; DISCONNECT hợp lệ hủy Will. Trên thay thế ClientId, cleanup cũ không được xóa phiên mới.
- Keepalive không có control packet quá 1.5 lần giá trị CONNECT thì đóng connection. Keepalive=0 tắt kiểm tra idle MQTT.

Không có TLS, WebSocket, MQTT5, QoS2, disk persistence hay clustering trong hai plan này. Java desktop dùng TCP nên cổng 9001/8000 không còn là điều kiện cho demo. Web dashboard cũ giữ dưới nhãn legacy và không tự đổi sang HiveMQ trong đường chạy mới.

## Giới hạn vận hành và lỗi

- Tối đa 32 client đồng thời; gói MQTT có body tối đa 65536 byte.
- Deadline CONNECT 10 giây; deadline hoàn thành gói sau byte đầu tiên 10 giây; không giới hạn idle session keepalive=0 bằng deadline gói.
- Queue outbound tối đa 256 gói/client, pending QoS1 tối đa 128/client. Slow client vượt giới hạn sẽ bị đóng, client khác vẫn hoạt động.
- Mỗi session có một writer tuần tự; router không thực hiện I/O mạng trong khóa dùng chung.
- EOF, frame sai, UTF-8 sai, queue đầy, bind lỗi và lỗi cấu hình có thông báo cụ thể; không nuốt exception rồi báo thành công.
- Không tự dừng dịch vụ đang giữ cổng; in PID/cổng và cách chọn cổng khác.
- Credential được nạp từ properties ngoài JAR. Tài khoản demo được ghi rõ dùng trong lab; không có yêu cầu quản lý user phức tạp.

## Ứng dụng Java

Desktop có trường broker host/port, username/password, Connect/Disconnect, nhiệt độ/độ ẩm, badge broker và thiết bị, ON/OFF, log tối đa 50 dòng. Giữ giá trị hiển thị khi mất kết nối nhưng đánh dấu stale; lệnh LED bị vô hiệu khi offline. Callback mạng không chạm Swing trực tiếp: dùng SwingUtilities.invokeLater.

Auto-reconnect của Paho, kết nối lại thì subscribe lại ba topic. Khi broker mất kết nối, thiết bị là UNKNOWN; khi nhận Will là OFFLINE; ONLINE nhưng thiếu telemetry quá 10 giây là STALE. Không suy ra OFFLINE chỉ vì JSON lỗi.

## Kế hoạch chuyển test và tài liệu cũ

Maven/JUnit là bộ test chính của luồng Java. Bộ Python hiện có giữ làm regression cho hiện vật legacy và Arduino; tài liệu phải nói rõ tên/cấu hình nào bị thay thế. Không sửa test cũ thành kiểm tra sự tồn tại của file rỗng chỉ để có tổng số pass đẹp.

Bổ sung test socket thật và client Paho độc lập để tránh encoder/decoder tự khớp sai. Unit test topic, codec và model không thay cho smoke test trên hotspot.

## Nghiệm thu ba máy

1. Cả ba checkout cùng commit triển khai, chuẩn bị JDK21+, Maven nếu build tại chỗ, và PlatformIO/Wokwi ở máy 2. Cache dependencies trước buổi demo; không mặc định key Wokwi bảo đảm simulation chạy hoàn toàn offline.
2. Máy 1 xác định IPv4 đúng adapter hotspot; khởi động JAR; chỉ mở inbound TCP1883 cho subnet hotspot bằng hướng dẫn explicit.
3. Máy 2/3 chạy Test-NetConnection tới IP máy 1. Nếu client isolation chặn peer traffic, đổi cấu hình hotspot/router; không tuyên bố lỗi có thể sửa bằng code broker.
4. Máy 3 đăng nhập, thấy CONNECTED. Máy 2 build firmware có đúng IP và chạy Wokwi.
5. Đổi DHT22, máy 3 thấy nhiệt độ/độ ẩm trong hai chu kỳ gửi; ON/OFF từ máy 3 điều khiển LED máy 2.
6. Ngắt simulation đột ngột: thấy OFFLINE sau timeout keepalive; khởi động lại: ONLINE và telemetry phục hồi. Khởi động lại broker: dashboard tự kết nối lại và subscribe lại.
7. Thử sai password; tạo thêm client CLI; kiểm tra không có crosstalk từ broker public.
8. Ghi IP, commit, thời gian, kết quả từng bước và log/screenshot tương ứng trong biên bản; không ghi mật khẩu/key.

## Tài liệu kỹ thuật đã đối chiếu

- [OASIS MQTT 3.1.1](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html): framing, acknowledgement, session, retained và Will.
- [Java 21 ServerSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html) và [Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): server TCP và virtual threads.
- [Wokwi VS Code gateway](https://docs.wokwi.com/vscode/project-config#iot-gateway-esp32-wifi): đường từ ESP32 giả lập tới LAN.
- [PubSubClient](https://pubsubclient.knolleary.net/api.html): API Arduino và subscription QoS0/1.
- [Eclipse Paho Java](https://github.com/eclipse-paho/paho.mqtt.java): client Java độc lập với broker.
- [Swing EDT](https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html): cập nhật UI theo event dispatch thread.
- [Maven Java target](https://maven.apache.org/guides/mini/guide-java-target.html), [executable JAR](https://maven.apache.org/plugins/maven-shade-plugin/examples/executable-jar.html), [JUnit 5.13.4](https://docs.junit.org/5.13.4/release-notes/), [Gson 2.13.2](https://github.com/google/gson/releases/tag/gson-parent-2.13.2).

## Sửa đổi phạm vi MVP (2026-11-14)

Người dùng duyệt làm MVP trước. Phiên bản đầu **hoãn** các mục sau: QoS1 outbound (mọi subscription được cấp QoS0, PUBLISH QoS1 vào vẫn được PUBACK), pending ID, giới hạn 32 client, queue 256/pending 128, deadline gói tuyệt đối, chống slow client. Will và keepalive 1.5× vẫn bắt buộc. Trước khi viết firmware LAN phải có spike chứng minh Wokwi mở được TCP tới máy broker.
