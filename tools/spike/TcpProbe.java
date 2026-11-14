// Spike S0 (throwaway): TCP reachability probe for the Wokwi-simulated ESP32.
// Run: java tools/spike/TcpProbe.java <port>
// Binds 0.0.0.0:<port>, logs each connection + first <=32 bytes (hex),
// replies MQTT CONNACK accepted (20 02 00 00) and holds the socket 30 s.

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalTime;
import java.util.Collections;

public class TcpProbe {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1883;

        System.out.println("Local IPv4 addresses:");
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp()) continue;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address) {
                    System.out.println("  " + a.getHostAddress() + "  (" + ni.getDisplayName() + ")");
                }
            }
        }

        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", port));
            log("Listening on 0.0.0.0:" + port);
            while (true) {
                Socket s = server.accept();
                Thread t = new Thread(() -> handle(s));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private static void handle(Socket s) {
        String peer = String.valueOf(s.getRemoteSocketAddress());
        log("ACCEPT from " + peer);
        try (s) {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            byte[] buf = new byte[32];
            int n = 0;
            try {
                n = in.read(buf);
            } catch (SocketTimeoutException e) {
                log("  no bytes within 5 s from " + peer);
            }
            if (n > 0) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < n; i++) hex.append(String.format("%02x ", buf[i]));
                log("  first " + n + " bytes: " + hex.toString().trim());
            } else if (n < 0) {
                log("  peer closed before sending data");
            }
            OutputStream out = s.getOutputStream();
            out.write(new byte[] {0x20, 0x02, 0x00, 0x00});
            out.flush();
            log("  sent CONNACK 20 02 00 00 to " + peer + ", holding 30 s");
            Thread.sleep(30_000);
        } catch (Exception e) {
            log("  error with " + peer + ": " + e);
        }
        log("CLOSE " + peer);
    }

    private static void log(String msg) {
        System.out.println("[" + LocalTime.now().withNano(0) + "] " + msg);
    }
}
