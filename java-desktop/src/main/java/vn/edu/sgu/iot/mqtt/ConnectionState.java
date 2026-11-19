package vn.edu.sgu.iot.mqtt;

/** Lifecycle of a broker connection as seen by a UI or CLI. */
public enum ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}
