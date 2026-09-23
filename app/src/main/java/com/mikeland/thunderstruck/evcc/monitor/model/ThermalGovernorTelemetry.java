package com.mikeland.thunderstruck.evcc.monitor.model;

import org.json.JSONArray;
import org.json.JSONObject;

public class ThermalGovernorTelemetry {

    public static class ChargerGovernorInfo {
        public int id = 1;
        public boolean isDerated = false;
        public float temp = 0.0f;
        public float scale = 1.0f;
        public float targetAmps = 0.0f;
        public String statusText = "Optimal";
    }

    public boolean enabled = true;
    public boolean isDerated = false;
    public float baselineMaxc = 0.0f;
    public float activeMaxc = 0.0f;
    public int deratePercent = 100;
    public float peakTemp = 0.0f;
    public float maxTemp = 75.0f;
    public String hottestCharger = "";
    public String statusText = "Optimal";
    public final ChargerGovernorInfo[] chargers = new ChargerGovernorInfo[4];

    public ThermalGovernorTelemetry() {
        for (int i = 0; i < 4; i++) {
            chargers[i] = new ChargerGovernorInfo();
            chargers[i].id = i + 1;
        }
    }

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        this.enabled = json.optBoolean("enabled", this.enabled);
        this.isDerated = json.optBoolean("isDerated", this.isDerated);
        this.baselineMaxc = (float) json.optDouble("baselineMaxc", this.baselineMaxc);
        this.activeMaxc = (float) json.optDouble("activeMaxc", this.activeMaxc);
        this.deratePercent = json.optInt("deratePercent", this.deratePercent);
        this.peakTemp = (float) json.optDouble("peakTemp", this.peakTemp);
        this.maxTemp = (float) json.optDouble("maxTemp", this.maxTemp);
        this.hottestCharger = json.optString("hottestCharger", this.hottestCharger);
        this.statusText = json.optString("statusText", this.statusText);

        JSONArray arr = json.optJSONArray("chargers");
        if (arr != null) {
            for (int i = 0; i < Math.min(arr.length(), 4); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    chargers[i].id = obj.optInt("id", i + 1);
                    chargers[i].isDerated = obj.optBoolean("isDerated", false);
                    chargers[i].temp = (float) obj.optDouble("temp", 0.0);
                    chargers[i].scale = (float) obj.optDouble("scale", 1.0);
                    chargers[i].targetAmps = (float) obj.optDouble("targetAmps", 0.0);
                    chargers[i].statusText = obj.optString("statusText", "Optimal");
                }
            }
        }
    }
}

