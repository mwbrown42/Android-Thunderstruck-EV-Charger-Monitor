package com.thunderstruck.evcc.monitor.model;

import org.json.JSONObject;

public class ThermalGovernorTelemetry {
    public boolean enabled = true;
    public boolean isDerated = false;
    public float baselineMaxc = 0.0f;
    public float activeMaxc = 0.0f;
    public int deratePercent = 100;
    public float peakTemp = 0.0f;
    public String hottestCharger = "";
    public String statusText = "Optimal";

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        this.enabled = json.optBoolean("enabled", this.enabled);
        this.isDerated = json.optBoolean("isDerated", this.isDerated);
        this.baselineMaxc = (float) json.optDouble("baselineMaxc", this.baselineMaxc);
        this.activeMaxc = (float) json.optDouble("activeMaxc", this.activeMaxc);
        this.deratePercent = json.optInt("deratePercent", this.deratePercent);
        this.peakTemp = (float) json.optDouble("peakTemp", this.peakTemp);
        this.hottestCharger = json.optString("hottestCharger", this.hottestCharger);
        this.statusText = json.optString("statusText", this.statusText);
    }
}

