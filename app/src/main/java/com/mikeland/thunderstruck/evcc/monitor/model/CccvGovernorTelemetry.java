package com.mikeland.thunderstruck.evcc.monitor.model;

import org.json.JSONObject;

public class CccvGovernorTelemetry {
    public boolean enabled = true;
    public boolean isTapering = false;
    public float packVoltage = 0.0f;
    public float cellVoltage = 0.0f;
    public float targetAmps = 50.0f;
    public float activeCurrent = 0.0f;
    public String phase = "BULK_CC";
    public String statusText = "Bulk Constant Current";
    public int writesThisSession = 0;
    public CccvProfile profile = new CccvProfile();

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        this.enabled = json.optBoolean("enabled", this.enabled);
        this.isTapering = json.optBoolean("isTapering", this.isTapering);
        this.packVoltage = (float) json.optDouble("packVoltage", this.packVoltage);
        this.cellVoltage = (float) json.optDouble("cellVoltage", this.cellVoltage);
        this.targetAmps = (float) json.optDouble("targetAmps", this.targetAmps);
        this.activeCurrent = (float) json.optDouble("activeCurrent", this.activeCurrent);
        this.phase = json.optString("phase", this.phase);
        this.statusText = json.optString("statusText", this.statusText);
        this.writesThisSession = json.optInt("writesThisSession", this.writesThisSession);

        profile.updateFromJson(json);
    }
}
