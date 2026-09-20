package com.mikeland.thunderstruck.evcc.monitor.model;

public class SessionDataPoint {
    public long timestamp;
    public float voltage;
    public float current;

    public SessionDataPoint(long timestamp, float voltage, float current) {
        this.timestamp = timestamp;
        this.voltage = voltage;
        this.current = current;
    }
}
