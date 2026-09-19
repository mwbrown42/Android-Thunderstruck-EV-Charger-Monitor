package com.thunderstruck.evcc.monitor.model;

import org.json.JSONObject;

public class EvccTelemetry {
    public String state = "STANDBY";
    public String j1772 = "DISCONNECTED";
    public String ip = "192.168.4.1";
    public boolean traceCan = false;
    public boolean traceState = false;
    public boolean traceCharger = false;

    public ChargerTelemetry charger1 = new ChargerTelemetry(40, "tsm2500");
    public ChargerTelemetry charger2 = new ChargerTelemetry(41, "tsm2500_41");
    public ThermalGovernorTelemetry governor = new ThermalGovernorTelemetry();
    public float maxv = 0.0f;
    public float maxc = 0.0f;
    public long sessionSec = 0;

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        
        JSONObject stateObj = json.optJSONObject("state");
        if (stateObj != null) {
            this.state = stateObj.optString("state", this.state);
            this.j1772 = stateObj.optString("j1772", this.j1772);
            this.sessionSec = stateObj.optLong("sessionSec", this.sessionSec);
            float v = (float) stateObj.optDouble("maxv", 0.0);
            float c = (float) stateObj.optDouble("maxc", 0.0);
            if (v > 0) this.maxv = v;
            if (c > 0) this.maxc = c;
        }

        this.ip = json.optString("ip", this.ip);

        JSONObject traces = json.optJSONObject("traces");
        if (traces != null) {
            this.traceCan = traces.optBoolean("can", this.traceCan);
            this.traceState = traces.optBoolean("state", this.traceState);
            this.traceCharger = traces.optBoolean("charger", this.traceCharger);
        }

        JSONObject c1 = json.optJSONObject("c1");
        if (c1 != null) charger1.updateFromJson(c1);

        JSONObject c2 = json.optJSONObject("c2");
        if (c2 != null) charger2.updateFromJson(c2);

        JSONObject gov = json.optJSONObject("governor");
        if (gov != null) governor.updateFromJson(gov);
    }

    public void reset() {
        this.state = "STANDBY";
        this.j1772 = "DISCONNECTED";
        this.traceCan = false;
        this.traceState = false;
        this.traceCharger = false;
        this.charger1 = new ChargerTelemetry(40, "tsm2500");
        this.charger2 = new ChargerTelemetry(41, "tsm2500_41");
        this.governor = new ThermalGovernorTelemetry();
    }
}
