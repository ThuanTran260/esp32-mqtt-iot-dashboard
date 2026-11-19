package vn.edu.sgu.mqtt.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/** Lists the machine's usable IPv4 addresses so users know what to type on other machines. */
public final class LanAddresses {
    private LanAddresses() {}

    /**
     * IPv4 addresses of adapters that are up and not loopback, each formatted as
     * {@code "<interface display name>: <ip>"}. Empty if none or if enumeration fails.
     */
    public static List<String> ipv4() {
        var result = new ArrayList<String>();
        try {
            for (NetworkInterface nic : NetworkInterface.networkInterfaces().toList()) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InetAddress address : nic.inetAddresses().toList()) {
                    if (address instanceof Inet4Address) {
                        result.add(nic.getDisplayName() + ": " + address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            // Hints only: fall through with whatever was collected.
        }
        return result;
    }
}
