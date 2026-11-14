# Spike S0: can the Wokwi ESP32 reach a TCP listener?

This is a throwaway spike. It decides whether the Java broker plan works for the demo. The question is whether the ESP32 simulated by Wokwi in VS Code on máy 2 can open TCP to:

- **(A)** a listener on máy 2 itself, through `host.wokwi.internal`
- **(B)** a listener on another machine on the same hotspot, through its IPv4

## What the spike uses

| Piece | Where | Notes |
|---|---|---|
| `TcpProbe` | `tools/spike/TcpProbe.java` | Listens on `0.0.0.0:<port>` and prints all local IPv4 addresses at startup. For each connection it logs the peer and the first 32 bytes or fewer in hex. It replies with MQTT CONNACK `20 02 00 00` and holds the socket for 30 s. Run it with `java tools/spike/TcpProbe.java 1883`. |
| `[env:spike]` | `platformio.ini` | Extends `esp32doit-devkit-v1`. It only adds `-DSPIKE_HOST=\"host.wokwi.internal\"`. |
| `#ifdef SPIKE_HOST` | `esp32_wokwi/sketch.ino` | Overrides only `mqtt_server`. The port stays at `1883`, so the probe **must listen on 1883**. |

The default env `esp32doit-devkit-v1` is unchanged and still targets `broker.hivemq.com`.

## Wokwi gateway setting

**No `wokwi.toml` setting is needed for outbound connections.** Per https://docs.wokwi.com/vscode/project-config, Wokwi for VS Code "includes a bundled version of the Wokwi Private IoT Gateway", which connects the simulated ESP32's WiFi (`Wokwi-GUEST`) to your local network and the Internet.

Per https://docs.wokwi.com/guides/esp32-wifi, the hostname `host.wokwi.internal` reaches the host machine's localhost. The gateway supports TCP and UDP but not ICMP, so ping cannot be used as a test.

`[[net.forward]]` in `wokwi.toml` is for the opposite direction: from the PC into the ESP32. This spike does not need it.

Caveat: the browser version of the Private Gateway is a paid feature. Whether the bundled VS Code gateway reaches **LAN IPs** (case B) under your licence is exactly what this spike measures.

## Steps for the human (VS Code on máy 2)

Do this **once** before the cases:

1. Open this worktree folder in VS Code. Wokwi must be activated: F1 → "Wokwi: Request a new License" if you haven't already.
2. Point Wokwi at the spike firmware **temporarily** by editing the root `wokwi.toml`:
   ```toml
   firmware = ".pio/build/spike/firmware.bin"
   elf = ".pio/build/spike/firmware.elf"
   ```
   Do not commit this change. Revert it afterwards with `git checkout -- wokwi.toml`.
3. Build: `pio run -e spike`.

### Case A: same machine (`host.wokwi.internal`)

1. In a terminal on máy 2, run `java tools/spike/TcpProbe.java 1883`. If Windows Firewall prompts, allow it on Private networks.
2. In VS Code: F1 → **Wokwi: Start Simulator**. Make sure `diagram.json` is in the root.
3. Watch the Wokwi serial monitor for `Attempting MQTT connection to host.wokwi.internal...`. Then watch the probe terminal for `ACCEPT from ...` and `first N bytes: 10 ...` (an MQTT CONNECT starts with `10`).
4. Record the result in the table. The sketch may log `connected`, or loop on a subscribe or ping error. Either way, the `ACCEPT` line is what counts.

### Case B: other machine on the hotspot (LAN IPv4)

1. On the other machine (máy 1 or máy 3), copy `tools/spike/TcpProbe.java` over and run `java TcpProbe.java 1883`. Note its hotspot IPv4 from the startup list. On an iPhone hotspot it looks like `172.20.10.x`.
2. On that machine, allow inbound TCP 1883 (run as admin):
   `netsh advfirewall firewall add rule name="spike1883" dir=in action=allow protocol=TCP localport=1883`
   Remove it afterwards with `netsh advfirewall firewall delete rule name="spike1883"`.
3. Sanity check from máy 2's own terminal, outside Wokwi:
   `powershell -c "Test-NetConnection <IP> -Port 1883"` should show `TcpTestSucceeded : True`. If it shows False, the problem is the hotspot or firewall, not Wokwi. Fix that first.
4. On máy 2, temporarily edit `platformio.ini` `[env:spike]` to `build_flags = -DSPIKE_HOST=\"<IP>\"`. Run `pio run -e spike`, then restart the simulator.
5. Look for `ACCEPT from ...` in the probe on the other machine. Record the result.

### Case C: control (negative)

With **no** probe running, start the simulator on `host.wokwi.internal`. The serial monitor should show repeated `failed, rc=-2`. This proves an `ACCEPT` in case A was real.

### Cleanup

Run `git checkout -- wokwi.toml platformio.ini` to drop the temporary edits. Then delete the firewall rule from case B.

## Results

| Case | Target | Expected | Actual |
|---|---|---|---|
| A | `host.wokwi.internal:1883` (probe on máy 2) | Probe logs `ACCEPT` + bytes starting with `10` | NOT RUN |
| B | `<other machine IPv4>:1883` (probe on máy 1/3) | Probe logs `ACCEPT` + bytes starting with `10` | NOT RUN |
| C | `host.wokwi.internal:1883`, no probe | Sketch logs `failed, rc=-2` repeatedly | NOT RUN |

How to read the results:

- **A and B pass:** the Java broker can live on any machine.
- **Only A passes:** the broker must run on máy 2, the Wokwi machine.
- **Neither passes:** the Java broker plan is not viable with Wokwi. Fall back to the public broker.
