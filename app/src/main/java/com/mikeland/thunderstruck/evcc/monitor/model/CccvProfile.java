package com.mikeland.thunderstruck.evcc.monitor.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CccvProfile {
    public static class Point {
        public float voltage = 0.0f;
        public float current = 0.0f;

        public Point(float v, float c) {
            this.voltage = v;
            this.current = c;
        }
    }

    public boolean enabled = true;
    public int cellCount = 36;
    public boolean smoothLinear = false;
    public boolean fastCutoff = true;
    public float terminationAmps = 4.0f;
    public List<Point> points = new ArrayList<>();

    public CccvProfile() {
        setDefaultTesla36S();
    }

    public void setDefaultTesla36S() {
        enabled = true;
        cellCount = 36;
        smoothLinear = false;
        fastCutoff = true;
        terminationAmps = 4.0f;
        points.clear();
        points.add(new Point(143.3f, 50.0f));
        points.add(new Point(145.4f, 35.0f));
        points.add(new Point(146.5f, 20.0f));
        points.add(new Point(147.2f, 10.0f));
        points.add(new Point(147.6f, 4.0f));
    }

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        this.enabled = json.optBoolean("enabled", this.enabled);
        this.cellCount = json.optInt("cellCount", this.cellCount);
        this.smoothLinear = json.optBoolean("smoothLinear", this.smoothLinear);
        this.fastCutoff = json.optBoolean("fastCutoff", this.fastCutoff);
        this.terminationAmps = (float) json.optDouble("termAmps", this.terminationAmps);

        JSONArray pts = json.optJSONArray("points");
        if (pts != null && pts.length() > 0) {
            points.clear();
            for (int i = 0; i < pts.length(); i++) {
                JSONObject p = pts.optJSONObject(i);
                if (p != null) {
                    float v = (float) p.optDouble("v", 0.0);
                    float a = (float) p.optDouble("a", 0.0);
                    points.add(new Point(v, a));
                }
            }
        }
    }
}
