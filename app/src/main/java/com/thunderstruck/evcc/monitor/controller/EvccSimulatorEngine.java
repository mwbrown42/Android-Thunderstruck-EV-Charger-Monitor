package com.thunderstruck.evcc.monitor.controller;

import android.os.Handler;
import android.os.Looper;
import com.thunderstruck.evcc.monitor.model.EvccTelemetry;
import com.thunderstruck.evcc.monitor.model.ChargerTelemetry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EvccSimulatorEngine {

    public enum Scenario {
        DUAL_CHARGE(0, "Dual Charge (18A ea)"),
        SINGLE_CHARGE(1, "Single Charger"),
        TAPERING(2, "CV Tapering Phase"),
        OVERTEMP_FAULT(3, "Overtemp Fault"),
        CAN_RXERR(4, "CAN RX Error"),
        INPUT_VOLTAGE_ERR(5, "Input Volt Err"),
        PACK_VOLTAGE_ERR(6, "Pack Volt Err"),
        STANDBY(7, "Standby Mode");

        public final int id;
        public final String label;
        Scenario(int id, String label) { this.id = id; this.label = label; }
    }

    public interface SimulatorListener {
        void onTelemetryUpdate(EvccTelemetry telemetry);
        void onRawLineEmitted(String line, boolean isTx);
        void onQueryResponse(String queryType, String text);
    }

    private static EvccSimulatorEngine instance;
    private ScheduledExecutorService executor;
    private volatile boolean isRunning = false;
    private SimulatorListener listener;

    private Scenario currentScenario = Scenario.DUAL_CHARGE;
    private final EvccTelemetry telemetry = new EvccTelemetry();

    // Tunable parameters (matching EVCC defaults for 128-150V pack)
    private float maxv = 148.0f;
    private float maxc = 20.0f;
    private float termc = 1.5f;

    // Simulation accumulator physics
    private long lastTickTime = 0;
    private int tickCounter = 0;

    public static synchronized EvccSimulatorEngine getInstance() {
        if (instance == null) {
            instance = new EvccSimulatorEngine();
        }
        return instance;
    }

    private EvccSimulatorEngine() {
        applyScenario(Scenario.DUAL_CHARGE);
    }

    public void setListener(SimulatorListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (!isRunning) {
            isRunning = true;
            lastTickTime = System.currentTimeMillis();
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(() -> {
                try {
                    if (!isRunning) return;
                    stepPhysics();
                    if (listener != null) {
                        listener.onTelemetryUpdate(telemetry);
                    }
                } catch (Exception ignored) {}
            }, 0, 500, TimeUnit.MILLISECONDS);

            if (listener != null) {
                listener.onRawLineEmitted("[Sim] EVCC Simulator Engine STARTED at 9600 baud.\n", false);
            }
        }
    }

    public synchronized void stop() {
        if (isRunning) {
            isRunning = false;
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            if (listener != null) {
                listener.onRawLineEmitted("[Sim] EVCC Simulator Engine STOPPED.\n", false);
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void applyScenario(Scenario scenario) {
        this.currentScenario = scenario;
        ChargerTelemetry c1 = telemetry.charger1;
        ChargerTelemetry c2 = telemetry.charger2;

        c1.rxerr = false; c1.hwfail = false; c1.overtemp = false;
        c1.notCharging = false; c1.inputVoltageErr = false; c1.packVoltageErr = false;
        c2.rxerr = false; c2.hwfail = false; c2.overtemp = false;
        c2.notCharging = false; c2.inputVoltageErr = false; c2.packVoltageErr = false;

        switch (scenario) {
            case DUAL_CHARGE:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 142.0f; c1.current = 22.0f; c1.temperature = 38.0f; c1.active = true;
                c2.voltage = 142.0f; c2.current = 22.0f; c2.temperature = 39.0f; c2.active = true;
                break;
            case SINGLE_CHARGE:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 142.0f; c1.current = 22.0f; c1.temperature = 36.0f; c1.active = true;
                c2.voltage = 0.0f;   c2.current = 0.0f;  c2.temperature = 24.0f; c2.active = false;
                break;
            case TAPERING:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 133.8f; c1.current = 4.2f;  c1.temperature = 44.0f; c1.active = true;
                c2.voltage = 133.8f; c2.current = 4.1f;  c2.temperature = 45.0f; c2.active = true;
                break;
            case OVERTEMP_FAULT:
                telemetry.state = "FAULT";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 124.0f; c1.current = 0.0f;  c1.temperature = 68.0f; c1.active = false; c1.overtemp = true;
                c2.voltage = 124.0f; c2.current = 0.0f;  c2.temperature = 69.0f; c2.active = false; c2.overtemp = true;
                break;
            case CAN_RXERR:
                telemetry.state = "FAULT";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 120.0f; c1.current = 0.0f;  c1.temperature = 35.0f; c1.active = false; c1.rxerr = true;
                c2.voltage = 120.0f; c2.current = 0.0f;  c2.temperature = 35.0f; c2.active = false; c2.rxerr = true;
                break;
            case INPUT_VOLTAGE_ERR:
                telemetry.state = "FAULT";
                telemetry.j1772 = "CONNECTED";
                c1.voltage = 0.0f;   c1.current = 0.0f;  c1.temperature = 28.0f; c1.active = false; c1.inputVoltageErr = true;
                c2.voltage = 0.0f;   c2.current = 0.0f;  c2.temperature = 28.0f; c2.active = false; c2.inputVoltageErr = true;
                break;
            case PACK_VOLTAGE_ERR:
                telemetry.state = "FAULT";
                telemetry.j1772 = "LOCKED";
                c1.voltage = 142.0f; c1.current = 0.0f;  c1.temperature = 34.0f; c1.active = false; c1.packVoltageErr = true;
                c2.voltage = 142.0f; c2.current = 0.0f;  c2.temperature = 34.0f; c2.active = false; c2.packVoltageErr = true;
                break;
            case STANDBY:
                telemetry.state = "STANDBY";
                telemetry.j1772 = "DISCONNECTED";
                c1.voltage = 0.0f;   c1.current = 0.0f;  c1.temperature = 22.0f; c1.active = false;
                c2.voltage = 0.0f;   c2.current = 0.0f;  c2.temperature = 22.0f; c2.active = false;
                break;
        }

        c1.power = c1.voltage * c1.current;
        c2.power = c2.voltage * c2.current;

        updateSimulatedGovernor();

        if (listener != null) {
            listener.onTelemetryUpdate(telemetry);
        }
    }

    public void handleGovernorToggle(boolean enabled) {
        telemetry.governor.enabled = enabled;
        updateSimulatedGovernor();
        if (listener != null) {
            listener.onTelemetryUpdate(telemetry);
            listener.onRawLineEmitted("[Sim Governor] Thermal Governor " + (enabled ? "ENABLED" : "DISABLED") + "\n", false);
        }
    }

    private void updateSimulatedGovernor() {
        if (!telemetry.governor.enabled) {
            telemetry.governor.isDerated = false;
            telemetry.governor.baselineMaxc = maxc;
            telemetry.governor.activeMaxc = maxc;
            telemetry.governor.deratePercent = 100;
            telemetry.governor.statusText = "Disabled";
            return;
        }

        float maxT = Math.max(telemetry.charger1.temperature, telemetry.charger2.temperature);
        String hottest = (telemetry.charger1.temperature >= telemetry.charger2.temperature) ? "tsm2500" : "tsm2500_41";
        telemetry.governor.peakTemp = maxT;
        telemetry.governor.hottestCharger = hottest;
        telemetry.governor.baselineMaxc = maxc;

        if (maxT >= 63.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 30;
            telemetry.governor.activeMaxc = Math.max(6.0f, maxc * 0.30f);
            telemetry.governor.statusText = "Emergency Derate (Peak >= 63Â°C)";
        } else if (maxT >= 60.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 50;
            telemetry.governor.activeMaxc = Math.max(6.0f, maxc * 0.50f);
            telemetry.governor.statusText = "Heavy Derate (Peak 60-62Â°C)";
        } else if (maxT >= 57.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 70;
            telemetry.governor.activeMaxc = Math.max(6.0f, maxc * 0.70f);
            telemetry.governor.statusText = "Moderate Derate (Peak 57-59Â°C)";
        } else if (maxT >= 53.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 85;
            telemetry.governor.activeMaxc = Math.max(6.0f, maxc * 0.85f);
            telemetry.governor.statusText = "Warning Derate (Peak 53-56Â°C)";
        } else {
            telemetry.governor.isDerated = false;
            telemetry.governor.deratePercent = 100;
            telemetry.governor.activeMaxc = maxc;
            telemetry.governor.statusText = "Optimal";
        }
    }

    public Scenario getCurrentScenario() {
        return currentScenario;
    }

    public EvccTelemetry getTelemetry() {
        return telemetry;
    }

    public void setChargerValues(int chargerId, float v, float a, float tmp) {
        ChargerTelemetry c = (chargerId == 40 || chargerId == 1) ? telemetry.charger1 : telemetry.charger2;
        c.voltage = v;
        c.current = a;
        c.temperature = tmp;
        c.power = v * a;
        c.active = (a > 0.1f && v > 10.0f);
        updateSimulatedGovernor();
        if (listener != null) listener.onTelemetryUpdate(telemetry);
    }

    public void setChargerFault(int chargerId, String faultKey, boolean value) {
        ChargerTelemetry c = (chargerId == 40 || chargerId == 1) ? telemetry.charger1 : telemetry.charger2;
        if ("rxerr".equalsIgnoreCase(faultKey)) c.rxerr = value;
        else if ("hwfail".equalsIgnoreCase(faultKey)) c.hwfail = value;
        else if ("overtemp".equalsIgnoreCase(faultKey)) c.overtemp = value;
        else if ("not_charging".equalsIgnoreCase(faultKey)) c.notCharging = value;
        else if ("input_voltage_err".equalsIgnoreCase(faultKey)) c.inputVoltageErr = value;
        else if ("pack_voltage_err".equalsIgnoreCase(faultKey)) c.packVoltageErr = value;
        if (listener != null) listener.onTelemetryUpdate(telemetry);
    }

    public void setSystemState(String state, String j1772) {
        if (state != null) telemetry.state = state;
        if (j1772 != null) telemetry.j1772 = j1772;
        if (listener != null) listener.onTelemetryUpdate(telemetry);
    }

    public void handleCommand(String rawCommand) {
        if (rawCommand == null) return;
        String cmd = rawCommand.trim();
        if (listener != null) {
            listener.onRawLineEmitted("> " + cmd + "\n", true);
        }

        String lower = cmd.toLowerCase(Locale.US);
        if (lower.equals("show") || lower.equals("show status")) {
            String resp = String.format(Locale.US,
                "--- EVCC STATUS ---\nState: %s\nJ1772: %s\nChargers: 2 detected\nTSM2500 #1 (ID 40): %.1fV, %.1fA, %.0fW, %.1fWh, %.0fC\nTSM2500 #2 (ID 41): %.1fV, %.1fA, %.0fW, %.1fWh, %.0fC\nMaxV: %.1fV | MaxC: %.1fA | TermC: %.1fA\n",
                telemetry.state, telemetry.j1772,
                telemetry.charger1.voltage, telemetry.charger1.current, telemetry.charger1.power, telemetry.charger1.wattHours, telemetry.charger1.temperature,
                telemetry.charger2.voltage, telemetry.charger2.current, telemetry.charger2.power, telemetry.charger2.wattHours, telemetry.charger2.temperature,
                maxv, maxc, termc);
            if (listener != null) {
                listener.onRawLineEmitted(resp, false);
                listener.onQueryResponse("SHOW", resp);
            }
        } else if (lower.equals("show config")) {
            String resp = String.format(Locale.US,
                "--- EVCC CONFIGURATION ---\nProtocol: CAN 2.0B 250kbps / TSM2500\nCharger 1 ID: 40 (0x28)\nCharger 2 ID: 41 (0x29)\nmaxv: %.1f V\nmaxc: %.1f A\ntermc: %.1f A\ntrace: can=%s state=%s charger=%s\n",
                maxv, maxc, termc,
                telemetry.traceCan ? "ON" : "OFF", telemetry.traceState ? "ON" : "OFF", telemetry.traceCharger ? "ON" : "OFF");
            if (listener != null) {
                listener.onRawLineEmitted(resp, false);
                listener.onQueryResponse("CONFIG", resp);
            }
        } else if (lower.equals("show history")) {
            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
            String resp = String.format(Locale.US,
                "--- CHARGE HISTORY LOG ---\nSession: %s\nC1 Energy: %.2f kWh | Peak V: %.1fV | Peak A: %.1fA\nC2 Energy: %.2f kWh | Peak V: %.1fV | Peak A: %.1fA\nTotal Wh Delivered: %.1f Wh\nStatus: NORMAL CHARGE COMPLETED\n",
                timeStr, telemetry.charger1.wattHours / 1000f, telemetry.charger1.voltage, maxc,
                telemetry.charger2.wattHours / 1000f, telemetry.charger2.voltage, maxc,
                telemetry.charger1.wattHours + telemetry.charger2.wattHours);
            if (listener != null) {
                listener.onRawLineEmitted(resp, false);
                listener.onQueryResponse("HISTORY", resp);
            }
        } else if (lower.startsWith("trace")) {
            if (lower.contains("can")) telemetry.traceCan = !telemetry.traceCan;
            if (lower.contains("state")) telemetry.traceState = !telemetry.traceState;
            if (lower.contains("charger")) telemetry.traceCharger = !telemetry.traceCharger;
            if (lower.contains("off")) {
                telemetry.traceCan = false;
                telemetry.traceState = false;
                telemetry.traceCharger = false;
            }
            if (listener != null) {
                listener.onRawLineEmitted(String.format("Trace settings: CAN=%s, State=%s, Charger=%s\n",
                    telemetry.traceCan ? "ON" : "OFF", telemetry.traceState ? "ON" : "OFF", telemetry.traceCharger ? "ON" : "OFF"), false);
                listener.onTelemetryUpdate(telemetry);
            }
        } else if (lower.startsWith("set maxv")) {
            try {
                maxv = Float.parseFloat(cmd.split(" ")[2]);
                if (listener != null) listener.onRawLineEmitted(String.format(Locale.US, "OK: maxv set to %.1f V\n", maxv), false);
            } catch (Exception ignored) {}
        } else if (lower.startsWith("set maxc")) {
            try {
                maxc = Float.parseFloat(cmd.split(" ")[2]);
                if (listener != null) listener.onRawLineEmitted(String.format(Locale.US, "OK: maxc set to %.1f A\n", maxc), false);
            } catch (Exception ignored) {}
        } else if (lower.startsWith("set termc")) {
            try {
                termc = Float.parseFloat(cmd.split(" ")[2]);
                if (listener != null) listener.onRawLineEmitted(String.format(Locale.US, "OK: termc set to %.1f A\n", termc), false);
            } catch (Exception ignored) {}
        } else {
            if (listener != null) listener.onRawLineEmitted("EVCC: Command executed -> " + cmd + "\n", false);
        }
    }

    private void stepPhysics() {
        long now = System.currentTimeMillis();
        float dtHours = (now - lastTickTime) / 3600000.0f;
        lastTickTime = now;
        tickCounter++;

        ChargerTelemetry c1 = telemetry.charger1;
        ChargerTelemetry c2 = telemetry.charger2;

        if ("CHARGE".equals(telemetry.state)) {
            // Dynamic charging physics: slow voltage rise
            if (c1.active && !c1.hasAnyFault()) {
                c1.voltage = Math.min(c1.voltage + 0.05f, maxv);
                if (c1.voltage >= maxv - 2.0f) {
                    // Taper current in CV stage
                    float taperFactor = Math.max((maxv - c1.voltage) / 2.0f, 0.08f);
                    c1.current = Math.max(maxc * taperFactor, termc);
                }
                c1.power = c1.voltage * c1.current;
                c1.wattHours += c1.power * dtHours;
                if (c1.temperature < 48.0f) c1.temperature += 0.02f;
            }

            if (c2.active && !c2.hasAnyFault()) {
                c2.voltage = Math.min(c2.voltage + 0.05f, maxv);
                if (c2.voltage >= maxv - 2.0f) {
                    float taperFactor = Math.max((maxv - c2.voltage) / 2.0f, 0.08f);
                    c2.current = Math.max(maxc * taperFactor, termc);
                }
                c2.power = c2.voltage * c2.current;
                c2.wattHours += c2.power * dtHours;
                if (c2.temperature < 48.0f) c2.temperature += 0.02f;
            }

            // Emit trace strings periodically if enabled
            if (listener != null) {
                if (telemetry.traceCharger && tickCounter % 2 == 0) {
                    if (c1.active) {
                        listener.onRawLineEmitted(String.format(Locale.US,
                            "tsm2500: V=%.1f, A=%.1f, W=%.0f, Wh=%.1f, TMP=%.0fC%s\n",
                            c1.voltage, c1.current, c1.power, c1.wattHours, c1.temperature,
                            c1.hasAnyFault() ? " [FAULT]" : ""), false);
                    }
                    if (c2.active) {
                        listener.onRawLineEmitted(String.format(Locale.US,
                            "tsm2500_41: V=%.1f, A=%.1f, W=%.0f, Wh=%.1f, TMP=%.0fC%s\n",
                            c2.voltage, c2.current, c2.power, c2.wattHours, c2.temperature,
                            c2.hasAnyFault() ? " [FAULT]" : ""), false);
                    }
                }
                if (telemetry.traceCan && tickCounter % 3 == 0) {
                    listener.onRawLineEmitted(String.format(Locale.US,
                        "can_rx: ID=0x18FF50E5 LEN=8 DATA=[%02X %02X %02X %02X %02X %02X %02X %02X]\n",
                        (int)(c1.voltage * 10) >> 8, (int)(c1.voltage * 10) & 0xFF,
                        (int)(c1.current * 10) >> 8, (int)(c1.current * 10) & 0xFF,
                        (int)c1.temperature + 40, c1.hasAnyFault() ? 1 : 0, 0, 0), false);
                }
            }
        }
    }
}
