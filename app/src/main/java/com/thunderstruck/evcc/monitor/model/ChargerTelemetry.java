package com.thunderstruck.evcc.monitor.model;

import org.json.JSONObject;

public class ChargerTelemetry {
    public int id;                     // 40 for Charger 1, 41 for Charger 2
    public String name = "tsm2500";
    public float voltage = 0.0f;
    public float current = 0.0f;
    public float power = 0.0f;
    public float wattHours = 0.0f;
    public float temperature = 25.0f;
    public boolean active = false;

    // Fault flags
    public boolean rxerr = false;
    public boolean hwfail = false;
    public boolean overtemp = false;
    public boolean notCharging = false;
    public boolean inputVoltageErr = false;
    public boolean packVoltageErr = false;

    public ChargerTelemetry(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        this.voltage = (float) json.optDouble("v", this.voltage);
        this.current = (float) json.optDouble("a", this.current);
        this.power = (float) json.optDouble("w", this.power);
        this.wattHours = (float) json.optDouble("wh", this.wattHours);
        this.temperature = (float) json.optDouble("tmp", this.temperature);
        this.active = json.optBoolean("active", this.active);

        JSONObject faults = json.optJSONObject("faults");
        if (faults != null) {
            this.rxerr = faults.optBoolean("rxerr", false);
            this.hwfail = faults.optBoolean("hwfail", false);
            this.overtemp = faults.optBoolean("overtemp", false);
            this.notCharging = faults.optBoolean("not_charging", false);
            this.inputVoltageErr = faults.optBoolean("input_voltage_err", false);
            this.packVoltageErr = faults.optBoolean("pack_voltage_err", false);
        }
    }

    public boolean hasAnyFault() {
        return rxerr || hwfail || overtemp || notCharging || inputVoltageErr || packVoltageErr;
    }
}
