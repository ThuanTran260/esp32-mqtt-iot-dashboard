/**
 * Web Dashboard MQTT Client & UI Controller
 * Milestone 3: Web Gauge Dashboard
 * Connects via WebSocket to Mosquitto MQTT Broker (ws://localhost:9001)
 */

document.addEventListener("DOMContentLoaded", () => {
  // =========================================================================
  // 1. DOM Element Cache
  // =========================================================================
  const statusBadge = document.getElementById("status-badge");
  const deviceStatus = document.getElementById("device-status");
  const tempValue = document.getElementById("temp-value");
  const tempArc = document.getElementById("temp-arc");
  const humValue = document.getElementById("hum-value");
  const humArc = document.getElementById("hum-arc");
  const ledToggle = document.getElementById("led-toggle");
  const ledIndicator = document.getElementById("led-indicator");
  const ledStatusText = document.getElementById("led-status-text");
  const logBody = document.getElementById("log-body");
  const clearLogBtn = document.getElementById("clear-log-btn");

  // Constant limits & SVG Gauge parameters
  const MAX_LOG_ROWS = 50;
  const GAUGE_CIRCUMFERENCE = 502.65; // 2 * PI * 80px radius
  const GAUGE_ARC_LENGTH = 335.10;    // 240 degree sweep (240/360 * C)

  // =========================================================================
  // 2. MQTT Client Initialization & Options
  // =========================================================================
  // Cloud broker enabled for seamless Wokwi free simulation (no paid gateway needed)
  // Set USE_PUBLIC_BROKER = false to switch back to local Mosquitto (port 9001)
  const USE_PUBLIC_BROKER = true;
  const BROKER_URL = USE_PUBLIC_BROKER ? "wss://broker.hivemq.com:8884/mqtt" : "ws://localhost:9001";
  const TOPIC_PREFIX = "thuantran260/";
  const MQTT_OPTIONS = {
    clientId: "web_dashboard_" + Math.random().toString(16).substring(2, 10),
    username: "user1",
    password: "123456",
    clean: true,
    connectTimeout: 5000,
    reconnectPeriod: 2000
  };

  function updateStatusBadge(state, text) {
    if (!statusBadge) return;
    statusBadge.className = `badge status-badge status-${state}`;
    const labelSpan = statusBadge.querySelector(".badge-label");
    if (labelSpan) {
      labelSpan.textContent = text;
    } else {
      statusBadge.textContent = text;
    }
  }

  function updateDeviceStatus(statusStr) {
    if (!deviceStatus) return;
    const isOnline = (statusStr.toUpperCase() === "ONLINE");
    deviceStatus.className = `badge device-${isOnline ? "online" : "offline"}`;
    const labelSpan = deviceStatus.querySelector(".badge-label");
    if (labelSpan) {
      labelSpan.textContent = isOnline ? "ESP32: Online" : "ESP32: Offline";
    } else {
      deviceStatus.textContent = isOnline ? "ESP32: Online" : "ESP32: Offline";
    }
  }

  // Initial UI state
  updateStatusBadge("disconnected", "Disconnected (ws://localhost:9001)");
  updateDeviceStatus("OFFLINE");

  if (typeof mqtt === "undefined") {
    updateStatusBadge("error", "MQTT Library Missing");
    console.error("MQTT library is not defined. Ensure js/mqtt.min.js is present.");
    return;
  }

  // Connect to Mosquitto WebSocket listener on port 9001
  const client = mqtt.connect(BROKER_URL, {
    clientId: "web_dashboard_" + Math.random().toString(16).substring(2, 10),
    username: "user1",
    password: "123456",
    clean: true,
    connectTimeout: 5000,
    reconnectPeriod: 2000
  });

  // =========================================================================
  // 3. MQTT Client Lifecycle Event Handlers
  // =========================================================================
  client.on("connect", () => {
    updateStatusBadge("connected", "Connected");
    addMessageToLog("SYS", "system", "Connected to Mosquitto WebSocket Broker");

    // Register explicit individual subscriptions to telemetry & control topics
    client.subscribe("sensor/data", { qos: 0 }, (err) => {
      if (err) console.error("Failed to subscribe to sensor/data:", err);
    });

    client.subscribe("sensor/led", { qos: 0 }, (err) => {
      if (err) console.error("Failed to subscribe to sensor/led:", err);
    });

    client.subscribe("sensor/status", { qos: 1 }, (err) => {
      if (err) console.error("Failed to subscribe to sensor/status:", err);
    });

    // Cloud private namespace subscriptions
    if (TOPIC_PREFIX) {
      client.subscribe(TOPIC_PREFIX + "sensor/data", { qos: 0 });
      client.subscribe(TOPIC_PREFIX + "sensor/led", { qos: 0 });
      client.subscribe(TOPIC_PREFIX + "sensor/status", { qos: 1 });
    }
  });

  client.on("reconnect", () => {
    updateStatusBadge("connecting", "Reconnecting...");
  });

  client.on("close", () => {
    updateStatusBadge("disconnected", "Disconnected");
  });

  client.on("offline", () => {
    updateStatusBadge("disconnected", "Offline");
  });

  client.on("error", (err) => {
    updateStatusBadge("error", "Connection Error");
    console.error("MQTT Client Error:", err);
  });

  // =========================================================================
  // 4. Incoming Message Router
  // =========================================================================
  client.on("message", (topic, message) => {
    const payload = message.toString();
    addMessageToLog("RX", topic, payload);

    if (topic === "sensor/data" || topic === TOPIC_PREFIX + "sensor/data") {
      updateDeviceStatus("ONLINE");
      try {
        const data = JSON.parse(payload);
        if (data && typeof data === "object") {
          if ("temp" in data && !isNaN(Number(data.temp))) {
            updateTemperature(Number(data.temp));
          }
          if ("hum" in data && !isNaN(Number(data.hum))) {
            updateHumidity(Number(data.hum));
          }
        }
      } catch (err) {
        console.warn("JSON parse error on sensor/data:", payload, err);
      }
    } else if (topic === "sensor/led" || topic === TOPIC_PREFIX + "sensor/led") {
      const state = payload.trim().toUpperCase();
      if (state === "ON" || state === "OFF") {
        syncLedSwitch(state === "ON");
      }
    } else if (topic === "sensor/status" || topic === TOPIC_PREFIX + "sensor/status") {
      const status = payload.trim().toUpperCase();
      updateDeviceStatus(status);
    }
  });

  // =========================================================================
  // 5. Dynamic SVG Gauge Logic
  // =========================================================================
  function updateTemperature(val) {
    if (tempValue) {
      tempValue.textContent = `${val.toFixed(1)} °C`;
    }
    if (tempArc) {
      // Range: -10°C to 60°C (70°C span)
      const clamped = Math.max(-10, Math.min(60, val));
      const percentage = (clamped - (-10)) / 70.0;
      const offset = GAUGE_ARC_LENGTH * (1.0 - percentage);
      tempArc.style.strokeDashoffset = offset;
    }
  }

  function updateHumidity(val) {
    if (humValue) {
      humValue.textContent = `${val.toFixed(1)} %`;
    }
    if (humArc) {
      // Range: 0% to 100% (100% span)
      const clamped = Math.max(0, Math.min(100, val));
      const percentage = clamped / 100.0;
      const offset = GAUGE_ARC_LENGTH * (1.0 - percentage);
      humArc.style.strokeDashoffset = offset;
    }
  }

  // =========================================================================
  // 6. Actuator Interaction & Anti-Echo State Synchronization
  // =========================================================================
  function syncLedSwitch(isOn) {
    if (ledToggle && ledToggle.checked !== isOn) {
      ledToggle.checked = isOn;
    }
    updateLedVisuals(isOn);
  }

  function updateLedVisuals(isOn) {
    if (ledIndicator) {
      if (isOn) {
        ledIndicator.classList.add("led-on");
        ledIndicator.classList.remove("led-off");
      } else {
        ledIndicator.classList.add("led-off");
        ledIndicator.classList.remove("led-on");
      }
    }
    if (ledStatusText) {
      ledStatusText.textContent = isOn ? "LED: ON" : "LED: OFF";
    }
  }

  if (ledToggle) {
    ledToggle.addEventListener("change", () => {
      const command = ledToggle.checked ? "ON" : "OFF";
      if (client && client.connected) {
        const pubTopic = TOPIC_PREFIX ? (TOPIC_PREFIX + "sensor/led") : "sensor/led";
        client.publish(pubTopic, command, { qos: 0 });
        client.publish("sensor/led", command, { qos: 0 });
        addMessageToLog("TX", pubTopic, command);
      } else {
        console.warn("MQTT client offline. Cannot publish command:", command);
      }
      updateLedVisuals(ledToggle.checked);
    });
  }

  // =========================================================================
  // 7. Message Log Table with FIFO 50-Row Capping & XSS Immunity
  // =========================================================================
  function addMessageToLog(direction, topic, payload) {
    if (!logBody) return;

    // Remove empty placeholder row if present
    const emptyRow = document.getElementById("empty-log-row");
    if (emptyRow) {
      emptyRow.remove();
    }

    const row = document.createElement("tr");

    const now = new Date();
    const timeStr = [
      String(now.getHours()).padStart(2, "0"),
      String(now.getMinutes()).padStart(2, "0"),
      String(now.getSeconds()).padStart(2, "0")
    ].join(":") + "." + String(now.getMilliseconds()).padStart(3, "0");

    // Time cell
    const timeCell = document.createElement("td");
    timeCell.className = "log-time";
    timeCell.textContent = timeStr;
    row.appendChild(timeCell);

    // Direction badge cell
    const dirCell = document.createElement("td");
    const dirBadge = document.createElement("span");
    dirBadge.className = `log-dir ${direction.toLowerCase()}`;
    dirBadge.textContent = direction;
    dirCell.appendChild(dirBadge);
    row.appendChild(dirCell);

    // Topic cell
    const topicCell = document.createElement("td");
    topicCell.className = "log-topic";
    topicCell.textContent = topic;
    row.appendChild(topicCell);

    // Payload cell (XSS safe via textContent)
    const payloadCell = document.createElement("td");
    payloadCell.className = "log-payload";
    payloadCell.textContent = payload;
    row.appendChild(payloadCell);

    // Prepend new row to table body (newest on top)
    logBody.prepend(row);

    // Strict FIFO capping at 50 rows
    while (logBody.children.length > MAX_LOG_ROWS) {
      logBody.removeChild(logBody.lastElementChild);
    }
  }

  if (clearLogBtn) {
    clearLogBtn.addEventListener("click", () => {
      if (logBody) {
        logBody.innerHTML = "";
      }
    });
  }
});
