package com.mikeland.thunderstruck.evcc.monitor.model;

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
    public ChargerTelemetry charger3 = new ChargerTelemetry(42, "tsm2500_42");
    public ChargerTelemetry charger4 = new ChargerTelemetry(43, "tsm2500_43");
    public ThermalGovernorTelemetry governor = new ThermalGovernorTelemetry();
    public CccvGovernorTelemetry cccv = new CccvGovernorTelemetry();
    public float maxv = 0.0f;
    public float maxc = 0.0f;
    public long sessionSec = 0;

    public ChargerTelemetry getCharger(int index) {
        switch (index) {
            case 0: return charger1;
            case 1: return charger2;
            case 2: return charger3;
            case 3: return charger4;
            default: return charger1;
        }
    }

    public int getActiveChargerCount() {
        int count = 1;
        if (charger4 != null && (charger4.active || charger4.current > 0.2f || charger4.voltage > 20.0f)) return 4;
        if (charger3 != null && (charger3.active || charger3.current > 0.2f || charger3.voltage > 20.0f)) return 3;
        if (charger2 != null && (charger2.active || charger2.current > 0.2f || charger2.voltage > 20.0f)) return 2;
        return count;
    }

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

        JSONObject c3 = json.optJSONObject("c3");
        if (c3 != null) charger3.updateFromJson(c3);

        JSONObject c4 = json.optJSONObject("c4");
        if (c4 != null) charger4.updateFromJson(c4);

        // Also check if chargers array is sent
        org.json.JSONArray arr = json.optJSONArray("chargers");
        if (arr != null) {
            for (int i = 0; i < arr.length() && i < 4; i++) {
                JSONObject chObj = arr.optJSONObject(i);
                if (chObj != null) {
                    getCharger(i).updateFromJson(chObj);
                }
            }
        }

        JSONObject gov = json.optJSONObject("governor");
        if (gov != null) governor.updateFromJson(gov);

        JSONObject cccvObj = json.optJSONObject("cccv");
        if (cccvObj != null) cccv.updateFromJson(cccvObj);
    }

    public void reset() {
        this.state = "STANDBY";
        this.j1772 = "DISCONNECTED";
        this.traceCan = false;
        this.traceState = false;
        this.traceCharger = false;
        this.charger1 = new ChargerTelemetry(40, "tsm2500");
        this.charger2 = new ChargerTelemetry(41, "tsm2500_41");
        this.charger3 = new ChargerTelemetry(42, "tsm2500_42");
        this.charger4 = new ChargerTelemetry(43, "tsm2500_43");
        this.governor = new ThermalGovernorTelemetry();
        this.cccv = new CccvGovernorTelemetry();
    }
}
