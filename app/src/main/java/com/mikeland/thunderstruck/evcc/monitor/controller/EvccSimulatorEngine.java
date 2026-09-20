package com.mikeland.thunderstruck.evcc.monitor.controller;

import com.mikeland.thunderstruck.evcc.monitor.model.EvccTelemetry;
import com.mikeland.thunderstruck.evcc.monitor.model.ChargerTelemetry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EvccSimulatorEngine {

    public enum Scenario {
        QUAD_CHARGE(0, "Quad (4 Chg)"),
        DUAL_CHARGE(1, "Dual (2 Chg)"),
        SINGLE_CHARGE(2, "Single (1 Chg)"),
        TAPERING(3, "CV Taper"),
        OVERTEMP_FAULT(4, "Overtemp (>=60°C)"),
        CAN_RXERR(5, "CAN RxErr"),
        VOLT_ERR(6, "Volt Err"),
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

    private Scenario currentScenario = Scenario.QUAD_CHARGE;
    private final EvccTelemetry telemetry = new EvccTelemetry();

    // Tunable parameters (matching EVCC defaults for 128-150V pack, up to 4 chargers)
    private float maxv = 148.0f;
    private float maxc = 80.0f;
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
        applyScenario(Scenario.QUAD_CHARGE);
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

        // Reset all 4 chargers to safe baseline
        for (int i = 0; i < 4; i++) {
            ChargerTelemetry c = telemetry.getCharger(i);
            c.rxerr = false; c.hwfail = false; c.overtemp = false;
            c.notCharging = false; c.inputVoltageErr = false; c.packVoltageErr = false;
            c.voltage = 0.0f; c.current = 0.0f; c.power = 0.0f; c.temperature = 22.0f;
            c.active = false;
        }

        switch (scenario) {
            case QUAD_CHARGE:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                for (int i = 0; i < 4; i++) {
                    ChargerTelemetry c = telemetry.getCharger(i);
                    c.voltage = 142.0f;
                    c.current = 20.0f;
                    c.temperature = 38.0f + (i * 1.5f);
                    c.active = true;
                }
                break;

            case DUAL_CHARGE:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                telemetry.charger1.voltage = 142.0f; telemetry.charger1.current = 20.0f;
                telemetry.charger1.temperature = 38.0f; telemetry.charger1.active = true;
                telemetry.charger2.voltage = 142.0f; telemetry.charger2.current = 20.0f;
                telemetry.charger2.temperature = 39.0f; telemetry.charger2.active = true;
                break;

            case SINGLE_CHARGE:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                telemetry.charger1.voltage = 142.0f; telemetry.charger1.current = 20.0f;
                telemetry.charger1.temperature = 36.0f; telemetry.charger1.active = true;
                break;

            case TAPERING:
                telemetry.state = "CHARGE";
                telemetry.j1772 = "LOCKED";
                telemetry.charger1.voltage = 147.2f; telemetry.charger1.current = 4.2f;
                telemetry.charger1.temperature = 44.0f; telemetry.charger1.active = true;
                telemetry.charger2.voltage = 147.2f; telemetry.charger2.current = 4.1f;
                telemetry.charger2.temperature = 45.0f; telemetry.charger2.active = true;
                break;

            case OVERTEMP_FAULT:
                telemetry.state = "FAULT";
                telemetry.j1772 = "LOCKED";
                telemetry.charger1.voltage = 135.0f; telemetry.charger1.current = 0.0f;
                telemetry.charger1.temperature = 61.5f; telemetry.charger1.active = false; telemetry.charger1.overtemp = true;
                telemetry.charger2.voltage = 135.0f; telemetry.charger2.current = 0.0f;
                telemetry.charger2.temperature = 62.0f; telemetry.charger2.active = false; telemetry.charger2.overtemp = true;
                break;

            case CAN_RXERR:
                telemetry.state = "FAULT";
                telemetry.j1772 = "LOCKED";
                telemetry.charger1.voltage = 120.0f; telemetry.charger1.current = 0.0f;
                telemetry.charger1.temperature = 35.0f; telemetry.charger1.active = false; telemetry.charger1.rxerr = true;
                telemetry.charger2.voltage = 120.0f; telemetry.charger2.current = 0.0f;
                telemetry.charger2.temperature = 35.0f; telemetry.charger2.active = false; telemetry.charger2.rxerr = true;
                break;

            case VOLT_ERR:
                telemetry.state = "FAULT";
                telemetry.j1772 = "CONNECTED";
                telemetry.charger1.temperature = 28.0f; telemetry.charger1.inputVoltageErr = true;
                telemetry.charger2.temperature = 28.0f; telemetry.charger2.packVoltageErr = true;
                break;

            case STANDBY:
                telemetry.state = "STANDBY";
                telemetry.j1772 = "DISCONNECTED";
                break;
        }

        for (int i = 0; i < 4; i++) {
            ChargerTelemetry c = telemetry.getCharger(i);
            c.power = c.voltage * c.current;
        }

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

        float maxT = 0.0f;
        String hottest = "tsm2500";
        for (int i = 0; i < 4; i++) {
            ChargerTelemetry c = telemetry.getCharger(i);
            if (c != null && (c.active || c.temperature > maxT)) {
                if (c.temperature > maxT) {
                    maxT = c.temperature;
                    hottest = c.name;
                }
            }
        }
        if (maxT == 0.0f) maxT = telemetry.charger1.temperature;

        telemetry.governor.peakTemp = maxT;
        telemetry.governor.hottestCharger = hottest;
        telemetry.governor.baselineMaxc = maxc;

        // Thunderstruck EVCC trips charging hard at 60°C.
        // Governor aggressively throttles between 50°C and 59°C to guarantee heatsink stays under 60°C.
        if (maxT >= 60.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 0;
            telemetry.governor.activeMaxc = 0.0f;
            telemetry.governor.statusText = "HARD TRIP (>= 60°C Cutoff)";
        } else if (maxT >= 57.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 40;
            telemetry.governor.activeMaxc = Math.max(4.0f, maxc * 0.40f);
            telemetry.governor.statusText = "Heavy Derate (57-59°C)";
        } else if (maxT >= 54.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 70;
            telemetry.governor.activeMaxc = Math.max(6.0f, maxc * 0.70f);
            telemetry.governor.statusText = "Moderate Derate (54-56°C)";
        } else if (maxT >= 50.0f) {
            telemetry.governor.isDerated = true;
            telemetry.governor.deratePercent = 85;
            telemetry.governor.activeMaxc = Math.max(8.0f, maxc * 0.85f);
            telemetry.governor.statusText = "Warning Derate (50-53°C)";
        } else {
            telemetry.governor.isDerated = false;
            telemetry.governor.deratePercent = 100;
            telemetry.governor.activeMaxc = maxc;
            telemetry.governor.statusText = "Optimal (<50°C)";
        }
    }

    public Scenario getCurrentScenario() {
        return currentScenario;
    }

    public EvccTelemetry getTelemetry() {
        return telemetry;
    }

    public void setChargerValues(int chargerId, float v, float a, float tmp) {
        int idx = 0;
        if (chargerId >= 40 && chargerId <= 43) idx = chargerId - 40;
        else if (chargerId >= 1 && chargerId <= 4) idx = chargerId - 1;

        ChargerTelemetry c = telemetry.getCharger(idx);
        c.voltage = v;
        c.current = a;
        c.temperature = tmp;
        c.power = v * a;
        c.active = (a > 0.1f && v > 10.0f);
        updateSimulatedGovernor();
        if (listener != null) listener.onTelemetryUpdate(telemetry);
    }

    public void setChargerFault(int chargerId, String faultKey, boolean value) {
        int idx = 0;
        if (chargerId >= 40 && chargerId <= 43) idx = chargerId - 40;
        else if (chargerId >= 1 && chargerId <= 4) idx = chargerId - 1;

        ChargerTelemetry c = telemetry.getCharger(idx);
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
            StringBuilder sb = new StringBuilder();
            sb.append("--- EVCC STATUS ---\n");
            sb.append("State: ").append(telemetry.state).append("\n");
            sb.append("J1772: ").append(telemetry.j1772).append("\n");
            int activeCount = telemetry.getActiveChargerCount();
            sb.append("Chargers: ").append(activeCount).append(" detected\n");
            for (int i = 0; i < activeCount; i++) {
                ChargerTelemetry c = telemetry.getCharger(i);
                sb.append(String.format(Locale.US, "TSM2500 #%d (ID %d): %.1fV, %.1fA, %.0fW, %.1fWh, %.0fC\n",
                    i + 1, c.id, c.voltage, c.current, c.power, c.wattHours, c.temperature));
            }
            sb.append(String.format(Locale.US, "MaxV: %.1fV | MaxC: %.1fA | TermC: %.1fA\n", maxv, maxc, termc));
            String resp = sb.toString();
            if (listener != null) {
                listener.onRawLineEmitted(resp, false);
                listener.onQueryResponse("SHOW", resp);
            }
        } else if (lower.equals("show config")) {
            StringBuilder sb = new StringBuilder();
            sb.append("--- EVCC CONFIGURATION ---\n");
            sb.append("Protocol: CAN 2.0B 250kbps / TSM2500\n");
            for (int i = 0; i < 4; i++) {
                ChargerTelemetry c = telemetry.getCharger(i);
                sb.append(String.format(Locale.US, "Charger %d ID: %d (0x%X)\n", i + 1, c.id, c.id));
            }
            sb.append(String.format(Locale.US, "maxv: %.1f V\nmaxc: %.1f A\ntermc: %.1f A\n", maxv, maxc, termc));
            sb.append(String.format(Locale.US, "trace: can=%s state=%s charger=%s\n",
                telemetry.traceCan ? "ON" : "OFF", telemetry.traceState ? "ON" : "OFF", telemetry.traceCharger ? "ON" : "OFF"));
            String resp = sb.toString();
            if (listener != null) {
                listener.onRawLineEmitted(resp, false);
                listener.onQueryResponse("CONFIG", resp);
            }
        } else if (lower.equals("show history")) {
            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
            StringBuilder sb = new StringBuilder();
            sb.append("--- CHARGE HISTORY LOG ---\n");
            sb.append("Session: ").append(timeStr).append("\n");
            float totalWh = 0;
            int activeCount = telemetry.getActiveChargerCount();
            for (int i = 0; i < activeCount; i++) {
                ChargerTelemetry c = telemetry.getCharger(i);
                totalWh += c.wattHours;
                sb.append(String.format(Locale.US, "C%d Energy: %.2f kWh | Peak V: %.1fV | Peak A: %.1fA\n",
                    i + 1, c.wattHours / 1000f, c.voltage, maxc));
            }
            sb.append(String.format(Locale.US, "Total Wh Delivered: %.1f Wh\nStatus: NORMAL CHARGE COMPLETED\n", totalWh));
            String resp = sb.toString();
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

        if ("CHARGE".equals(telemetry.state)) {
            for (int i = 0; i < 4; i++) {
                ChargerTelemetry c = telemetry.getCharger(i);
                if (c.active && !c.hasAnyFault()) {
                    c.voltage = Math.min(c.voltage + 0.05f, maxv);
                    if (c.voltage >= maxv - 2.0f) {
                        float taperFactor = Math.max((maxv - c.voltage) / 2.0f, 0.08f);
                        c.current = Math.max(maxc * taperFactor, termc);
                    }
                    c.power = c.voltage * c.current;
                    c.wattHours += c.power * dtHours;
                    if (c.temperature < 48.0f) c.temperature += 0.02f;
                }
            }

            if (listener != null) {
                if (telemetry.traceCharger && tickCounter % 2 == 0) {
                    for (int i = 0; i < 4; i++) {
                        ChargerTelemetry c = telemetry.getCharger(i);
                        if (c.active) {
                            listener.onRawLineEmitted(String.format(Locale.US,
                                "%s: V=%.1f, A=%.1f, W=%.0f, Wh=%.1f, TMP=%.0fC%s\n",
                                c.name, c.voltage, c.current, c.power, c.wattHours, c.temperature,
                                c.hasAnyFault() ? " [FAULT]" : ""), false);
                        }
                    }
                }
                if (telemetry.traceCan && tickCounter % 3 == 0) {
                    ChargerTelemetry c1 = telemetry.charger1;
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
