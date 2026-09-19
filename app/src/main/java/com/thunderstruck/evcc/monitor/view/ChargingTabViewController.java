package com.thunderstruck.evcc.monitor.view;

import com.thunderstruck.evcc.monitor.R;
import com.thunderstruck.evcc.monitor.SettingsActivity;
import com.thunderstruck.evcc.monitor.controller.EvccGatewayClient;
import com.thunderstruck.evcc.monitor.controller.EvccSessionLogger;
import com.thunderstruck.evcc.monitor.controller.EvccSimulatorEngine;
import com.thunderstruck.evcc.monitor.model.ChargerTelemetry;
import com.thunderstruck.evcc.monitor.model.EvccTelemetry;
import com.thunderstruck.evcc.monitor.model.SessionDataPoint;
import com.thunderstruck.evcc.monitor.model.ThermalGovernorTelemetry;
import android.content.Intent;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import android.content.res.Resources;
import android.util.Log;
import org.json.JSONObject;

import java.util.Locale;

public class ChargingTabViewController implements EvccGatewayClient.EvccEventListener {

    private final Context context;
    private final View rootView;
    private final EvccGatewayClient client = EvccGatewayClient.getInstance();
    private final EvccSimulatorEngine simulator = EvccSimulatorEngine.getInstance();
    private String lastEvccState = "";

    // Theme Cache
    private int themeTitleColor = Color.parseColor("#9CA3AF");
    private int themeValueColor = Color.WHITE;
    private int themeIndicatorColor = Color.parseColor("#06B6D4");
    private int themeNegativeColor = Color.parseColor("#EF4444");
    private int themeTickColor = Color.parseColor("#1E293B");
    private Typeface themeFont = null;

    private static final String PREF_LAST_ACTIVE_CHARGERS = "evcc_last_active_chargers";
    private static final String PREF_EVCC_MAXV = "evcc_param_maxv";
    private static final String PREF_EVCC_MAXC = "evcc_param_maxc";
    private static final java.util.regex.Pattern PATTERN_MAXV = java.util.regex.Pattern.compile("(?:maxv\\s*[:=]|maxv set to)\\s*([0-9]+(?:\\.[0-9]+)?)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern PATTERN_MAXC = java.util.regex.Pattern.compile("(?:maxc\\s*[:=]|maxc set to)\\s*([0-9]+(?:\\.[0-9]+)?)", java.util.regex.Pattern.CASE_INSENSITIVE);

    // Header Badges
    private Button btnSimToggle;
    private TextView badgeState;
    private TextView badgeJ1772;
    private TextView badgeIp;
    private TextView badgeGovernor;
    private TextView badgeChargeTimer;

    // Temperature Chart View
    private TemperatureChartView chartTemperature;

    // Charge Session Tracking for Trip Table and MQTT
    private boolean isCurrentlyCharging = false;
    private long chargeSessionStartMs = 0;
    private float chargeSessionStartSoc = 0f;
    private float lastTotalDeliveredWh = 0f;
    private float peakTempC1 = 0f;
    private float peakTempC2 = 0f;

    // Thermal Governor Views
    private View bannerThermalDerate;
    private TextView bannerDerateTitle, bannerDerateDesc;
    private Button btnDismissDerateBanner;
    private TextView lblGovDetail;
    private Switch switchGovernor;
    private boolean bannerDismissed = false;
    private boolean isUpdatingGovernorSwitch = false;

    // Charger 1 Views
    private TextView c1StatusBadge;
    private TextView c1ValV, c1ValA, c1ValW, c1ValWh, c1ValTmp;
    private TextView c1FaultRxerr, c1FaultHwfail, c1FaultOvertemp, c1FaultNotCharging, c1FaultInputErr, c1FaultPackErr;
    private ChargingChartView c1Chart;

    // Charger 2 Views
    private TextView c2StatusBadge;
    private TextView c2ValV, c2ValA, c2ValW, c2ValWh, c2ValTmp;
    private TextView c2FaultRxerr, c2FaultHwfail, c2FaultOvertemp, c2FaultNotCharging, c2FaultInputErr, c2FaultPackErr;
    private ChargingChartView c2Chart;

    // Sim Presets
    private Button[] scButtons = new Button[8];
    private SeekBar simSeekC1V, simSeekC1A;
    private TextView simLblC1V, simLblC1A;

    // Parameters & Traces
    private Button btnTrCan, btnTrState, btnTrChg, btnTrOff;
    private EditText inpMaxV, inpMaxC;
    private Button btnSetMaxV, btnSetMaxC;

    // Terminal
    private ScrollView termScroll;
    private TextView termLogText;
    private EditText termInput;
    private Button btnTermSend, btnClearTerminal, btnDiagUsb, btnViewEvccLog;

    public ChargingTabViewController(Context context, View rootView) {
        this.context = context;
        this.rootView = rootView;
        EvccSessionLogger.getInstance().init(context);
        initViews();
        setupListeners();
        applyTheme();

        client.setListener(this);
        refreshGatewayConfig();
    }

    private void initViews() {
        btnSimToggle = rootView.findViewById(R.id.btn_sim_toggle);
        badgeState = rootView.findViewById(R.id.badge_evcc_state);
        badgeJ1772 = rootView.findViewById(R.id.badge_j1772_state);
        badgeIp = rootView.findViewById(R.id.badge_gateway_ip);
        badgeGovernor = rootView.findViewById(R.id.badge_governor);
        badgeChargeTimer = rootView.findViewById(R.id.badge_charge_timer);
        chartTemperature = rootView.findViewById(R.id.chart_temperature);

        // Thermal Governor
        bannerThermalDerate = rootView.findViewById(R.id.banner_thermal_derate);
        bannerDerateTitle = rootView.findViewById(R.id.banner_derate_title);
        bannerDerateDesc = rootView.findViewById(R.id.banner_derate_desc);
        btnDismissDerateBanner = rootView.findViewById(R.id.btn_dismiss_derate_banner);
        lblGovDetail = rootView.findViewById(R.id.lbl_governor_detail);
        switchGovernor = rootView.findViewById(R.id.switch_governor);

        // Charger 1
        c1StatusBadge = rootView.findViewById(R.id.c1_status_badge);
        c1ValV = rootView.findViewById(R.id.c1_val_v);
        c1ValA = rootView.findViewById(R.id.c1_val_a);
        c1ValW = rootView.findViewById(R.id.c1_val_w);
        c1ValWh = rootView.findViewById(R.id.c1_val_wh);
        c1ValTmp = rootView.findViewById(R.id.c1_val_tmp);

        c1FaultRxerr = rootView.findViewById(R.id.c1_fault_rxerr);
        c1FaultHwfail = rootView.findViewById(R.id.c1_fault_hwfail);
        c1FaultOvertemp = rootView.findViewById(R.id.c1_fault_overtemp);
        c1FaultNotCharging = rootView.findViewById(R.id.c1_fault_not_charging);
        c1FaultInputErr = rootView.findViewById(R.id.c1_fault_input_voltage_err);
        c1FaultPackErr = rootView.findViewById(R.id.c1_fault_pack_voltage_err);
        c1Chart = rootView.findViewById(R.id.c1_chart);

        // Charger 2
        c2StatusBadge = rootView.findViewById(R.id.c2_status_badge);
        c2ValV = rootView.findViewById(R.id.c2_val_v);
        c2ValA = rootView.findViewById(R.id.c2_val_a);
        c2ValW = rootView.findViewById(R.id.c2_val_w);
        c2ValWh = rootView.findViewById(R.id.c2_val_wh);
        c2ValTmp = rootView.findViewById(R.id.c2_val_tmp);

        c2FaultRxerr = rootView.findViewById(R.id.c2_fault_rxerr);
        c2FaultHwfail = rootView.findViewById(R.id.c2_fault_hwfail);
        c2FaultOvertemp = rootView.findViewById(R.id.c2_fault_overtemp);
        c2FaultNotCharging = rootView.findViewById(R.id.c2_fault_not_charging);
        c2FaultInputErr = rootView.findViewById(R.id.c2_fault_input_voltage_err);
        c2FaultPackErr = rootView.findViewById(R.id.c2_fault_pack_voltage_err);
        c2Chart = rootView.findViewById(R.id.c2_chart);

        // Presets
        scButtons[0] = rootView.findViewById(R.id.sc_btn_0);
        scButtons[1] = rootView.findViewById(R.id.sc_btn_1);
        scButtons[2] = rootView.findViewById(R.id.sc_btn_2);
        scButtons[3] = rootView.findViewById(R.id.sc_btn_3);
        scButtons[4] = rootView.findViewById(R.id.sc_btn_4);
        scButtons[5] = rootView.findViewById(R.id.sc_btn_5);
        scButtons[6] = rootView.findViewById(R.id.sc_btn_6);
        scButtons[7] = rootView.findViewById(R.id.sc_btn_7);

        simSeekC1V = rootView.findViewById(R.id.sim_seek_c1_v);
        simSeekC1A = rootView.findViewById(R.id.sim_seek_c1_a);
        simLblC1V = rootView.findViewById(R.id.sim_lbl_c1_v);
        simLblC1A = rootView.findViewById(R.id.sim_lbl_c1_a);

        // Traces & Params
        btnTrCan = rootView.findViewById(R.id.btn_trace_can);
        btnTrState = rootView.findViewById(R.id.btn_trace_state);
        btnTrChg = rootView.findViewById(R.id.btn_trace_charger);
        btnTrOff = rootView.findViewById(R.id.btn_trace_off);
        inpMaxV = rootView.findViewById(R.id.inp_maxv);
        inpMaxC = rootView.findViewById(R.id.inp_maxc);
        btnSetMaxV = rootView.findViewById(R.id.btn_set_maxv);
        btnSetMaxC = rootView.findViewById(R.id.btn_set_maxc);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (inpMaxV != null) {
            inpMaxV.setText(prefs.getString(PREF_EVCC_MAXV, "134.4"));
        }
        if (inpMaxC != null) {
            inpMaxC.setText(prefs.getString(PREF_EVCC_MAXC, "20.0"));
        }

        // Terminal
        termScroll = rootView.findViewById(R.id.term_scroll);
        termLogText = rootView.findViewById(R.id.term_log_text);
        termInput = rootView.findViewById(R.id.term_input);
        btnTermSend = rootView.findViewById(R.id.btn_term_send);
        btnClearTerminal = rootView.findViewById(R.id.btn_clear_terminal);
        btnDiagUsb = rootView.findViewById(R.id.btn_diag_usb);
        btnViewEvccLog = rootView.findViewById(R.id.btn_view_evcc_log);

        resetUIState();
    }

    public void onTabSelected() {
        onResume();
    }

    public void onTabDeselected() {
        onPause();
    }

    public void onResume() {
        applyTheme();
        client.addListener(this);
    }

    public void onPause() {
        client.removeListener(this);
    }

    public void applyTheme() {
        themeTitleColor = Color.parseColor("#94A3B8");
        themeValueColor = Color.parseColor("#FFFFFF");
        themeIndicatorColor = Color.parseColor("#8B5CF6");
        themeNegativeColor = Color.parseColor("#EF4444");
        themeTickColor = Color.parseColor("#374151");

        // Apply theme font across entire view hierarchy
        if (themeFont != null) {
            applyFontToHierarchy(rootView, themeFont);
        }

        // Apply theme title colors
        setTextViewColor(rootView.findViewById(R.id.title_gateway_header), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_title_label), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_title_label), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_lbl_v), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_lbl_a), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_lbl_w), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_lbl_wh), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c1_lbl_tmp), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_lbl_v), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_lbl_a), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_lbl_w), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_lbl_wh), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.c2_lbl_tmp), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_sim_presets), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_sim_c1_v_title), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_sim_c1_a_title), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_params_traces), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_maxv_param), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_maxc_param), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_terminal), themeTitleColor);

        // Apply theme value colors
        setTextViewColor(c1ValV, themeValueColor);
        setTextViewColor(c1ValA, themeValueColor);
        setTextViewColor(c1ValW, themeValueColor);
        setTextViewColor(c1ValWh, themeValueColor);
        setTextViewColor(c1ValTmp, themeValueColor);
        setTextViewColor(c2ValV, themeValueColor);
        setTextViewColor(c2ValA, themeValueColor);
        setTextViewColor(c2ValW, themeValueColor);
        setTextViewColor(c2ValWh, themeValueColor);
        setTextViewColor(c2ValTmp, themeValueColor);
        setTextViewColor(simLblC1V, themeValueColor);
        setTextViewColor(simLblC1A, themeValueColor);

        // Apply theme action button colors
        if (btnSetMaxV != null) btnSetMaxV.setBackgroundColor(themeIndicatorColor);
        if (btnSetMaxC != null) btnSetMaxC.setBackgroundColor(themeIndicatorColor);
        if (btnTermSend != null) btnTermSend.setBackgroundColor(themeIndicatorColor);

        // Apply theme to histograms
        if (c1Chart != null) {
            c1Chart.applyTheme(themeFont, themeTitleColor, themeValueColor, themeIndicatorColor, themeNegativeColor, themeTickColor);
        }
        if (c2Chart != null) {
            c2Chart.applyTheme(themeFont, themeTitleColor, themeValueColor, themeIndicatorColor, themeNegativeColor, themeTickColor);
        }
    }

    private void applyFontToHierarchy(View view, Typeface font) {
        if (view == null || font == null) return;
        if (view instanceof TextView) {
            ((TextView) view).setTypeface(font);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFontToHierarchy(group.getChildAt(i), font);
            }
        }
    }

    private void setTextViewColor(@Nullable View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
    }

    private void setupListeners() {
        if (btnSimToggle != null) {
            btnSimToggle.setOnClickListener(v -> {
                boolean newMode = !client.isSimulationMode();
                client.setSimulationMode(newMode);
                updateSimButtonUI(newMode);
                if (c1Chart != null) c1Chart.clearData();
                if (c2Chart != null) c2Chart.clearData();
                if (!newMode) {
                    resetUIState();
                    refreshGatewayConfig();
                }
            });
        }

        // Quick Momentary Inspection Buttons in Header
        View btnShow = rootView.findViewById(R.id.btn_show_status);
        if (btnShow != null) btnShow.setOnClickListener(v -> client.sendQuery("show"));

        View btnCfg = rootView.findViewById(R.id.btn_show_config);
        if (btnCfg != null) btnCfg.setOnClickListener(v -> client.sendQuery("config"));

        View btnHist = rootView.findViewById(R.id.btn_show_history);
        if (btnHist != null) btnHist.setOnClickListener(v -> client.sendQuery("history"));

        View btnReset = rootView.findViewById(R.id.btn_reset_graph);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                if (c1Chart != null) c1Chart.clearData();
                if (c2Chart != null) c2Chart.clearData();
            });
        }

        // Preset buttons
        for (int i = 0; i < scButtons.length; i++) {
            final int idx = i;
            if (scButtons[i] != null) {
                scButtons[i].setOnClickListener(v -> {
                    simulator.applyScenario(EvccSimulatorEngine.Scenario.values()[idx]);
                    updatePresetButtonsUI(idx);
                });
            }
        }

        // Sliders
        if (simSeekC1V != null) {
            simSeekC1V.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float v = progress;
                        if (simLblC1V != null) simLblC1V.setText(String.format(Locale.US, "%.1fV", v));
                        simulator.setChargerValues(40, v, simulator.getTelemetry().charger1.current, simulator.getTelemetry().charger1.temperature);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (simSeekC1A != null) {
            simSeekC1A.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float a = progress;
                        if (simLblC1A != null) simLblC1A.setText(String.format(Locale.US, "%.1fA", a));
                        simulator.setChargerValues(40, simulator.getTelemetry().charger1.voltage, a, simulator.getTelemetry().charger1.temperature);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Trace buttons
        if (btnTrCan != null) btnTrCan.setOnClickListener(v -> client.sendTraceToggle("can"));
        if (btnTrState != null) btnTrState.setOnClickListener(v -> client.sendTraceToggle("state"));
        if (btnTrChg != null) btnTrChg.setOnClickListener(v -> client.sendTraceToggle("charger"));
        if (btnTrOff != null) btnTrOff.setOnClickListener(v -> client.sendTraceToggle("off"));

        // Param set buttons
        if (btnSetMaxV != null && inpMaxV != null) {
            btnSetMaxV.setOnClickListener(v -> {
                try {
                    float val = Float.parseFloat(inpMaxV.getText().toString());
                    client.sendParamSet("maxv", val);
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putString(PREF_EVCC_MAXV, String.format(Locale.US, "%.1f", val)).apply();
                    Toast.makeText(context, "Set maxv = " + val, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            });
        }

        if (btnSetMaxC != null && inpMaxC != null) {
            btnSetMaxC.setOnClickListener(v -> {
                try {
                    float val = Float.parseFloat(inpMaxC.getText().toString());
                    client.sendParamSet("maxc", val);
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putString(PREF_EVCC_MAXC, String.format(Locale.US, "%.1f", val)).apply();
                    Toast.makeText(context, "Set maxc = " + val, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            });
        }

        // Terminal send
        if (btnTermSend != null && termInput != null) {
            btnTermSend.setOnClickListener(v -> {
                String cmd = termInput.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    client.sendRawCommand(cmd);
                    termInput.setText("");
                }
            });
        }

        if (btnClearTerminal != null && termLogText != null) {
            btnClearTerminal.setOnClickListener(v -> termLogText.setText(""));
        }

        if (btnDiagUsb != null) {
            btnDiagUsb.setOnClickListener(v -> client.sendRawCommand("usb"));
        }

        if (btnViewEvccLog != null) {
            btnViewEvccLog.setOnClickListener(v -> showLogDialog());
        }

        // Thermal Governor listeners
        if (btnDismissDerateBanner != null && bannerThermalDerate != null) {
            btnDismissDerateBanner.setOnClickListener(v -> {
                bannerDismissed = true;
                bannerThermalDerate.setVisibility(View.GONE);
            });
        }

        if (switchGovernor != null) {
            switchGovernor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isUpdatingGovernorSwitch) {
                    client.sendGovernorToggle(isChecked);
                }
            });
        }

        if (badgeGovernor != null && switchGovernor != null) {
            badgeGovernor.setOnClickListener(v -> switchGovernor.toggle());
        }
    }

    private void updateSimButtonUI(boolean isSim) {
        if (btnSimToggle != null) {
            if (isSim) {
                btnSimToggle.setText("🧪 SIMULATOR");
                btnSimToggle.setTextColor(Color.WHITE);
                btnSimToggle.setBackgroundColor(Color.parseColor("#7C3AED")); // vivid purple
            } else {
                btnSimToggle.setText("⚡ LIVE DATA");
                btnSimToggle.setTextColor(Color.WHITE);
                btnSimToggle.setBackgroundColor(Color.parseColor("#059669")); // vivid emerald green
            }
        }
    }

    private void updatePresetButtonsUI(int activeIdx) {
        for (int i = 0; i < scButtons.length; i++) {
            if (scButtons[i] != null) {
                if (i == activeIdx) {
                    scButtons[i].setBackgroundColor(Color.parseColor("#8B5CF6"));
                    scButtons[i].setTextColor(Color.WHITE);
                } else {
                    scButtons[i].setBackgroundColor(Color.parseColor("#1F223A"));
                    scButtons[i].setTextColor(Color.parseColor("#E2E8F0"));
                }
            }
        }
    }

    private final StringBuilder terminalBuffer = new StringBuilder(4096);
    private static final int MAX_TERMINAL_BUFFER_CHARS = 3000;
    private EvccTelemetry lastTelemetry = null;

    private String lastConnectionStatus = "";
    private boolean lastConnected = false;

    public void refreshGatewayConfig() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean evccEnabled = prefs.getBoolean(SettingsActivity.EVCC_ENABLE_KEY, true);

        if (!evccEnabled) {
            if (!client.isSimulationMode()) {
                client.disconnectWebSocket();
            }
            updateSimButtonUI(false);
            updateBadgeIpUI(false, "Offline");
        } else if (!client.isSimulationMode()) {
            updateSimButtonUI(false);
            if (!client.isConnected()) {
                client.connectWebSocket();
                updateBadgeIpUI(false, "Offline");
            } else {
                updateBadgeIpUI(true, "Online");
            }
        } else {
            updateSimButtonUI(true);
            updateBadgeIpUI(true, "Simulated");
        }
    }

    private void updateBadgeIpUI(boolean connected, String status) {
        if (badgeIp != null) {
            badgeIp.setText(status);
            if (client.isSimulationMode()) {
                badgeIp.setTextColor(Color.parseColor("#C084FC"));
            } else if (connected) {
                badgeIp.setTextColor(Color.parseColor("#10B981"));
            } else {
                badgeIp.setTextColor(Color.parseColor("#EF4444"));
            }
        }
    }

    private int getPersistedActiveChargers() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_LAST_ACTIVE_CHARGERS, 2);
    }

    private void persistActiveChargers(int count) {
        if (count < 1) count = 1;
        if (count > 2) count = 2;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(PREF_LAST_ACTIVE_CHARGERS, 2) != count) {
            prefs.edit().putInt(PREF_LAST_ACTIVE_CHARGERS, count).apply();
            updateChargerCardVisibility();
        }
    }

    private void updateChargerCardVisibility() {
        if (rootView == null) return;
        View cardC2 = rootView.findViewById(R.id.card_charger2);
        if (cardC2 != null) {
            int count = getPersistedActiveChargers();
            boolean showC2 = (count >= 2);
            cardC2.setVisibility(showC2 ? View.VISIBLE : View.GONE);
            if (!showC2 && c2Chart != null) {
                c2Chart.clearData();
            }
        }
    }

    private void parseAndStoreEvccConfigParams(String text) {
        if (text == null || text.isEmpty()) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        java.util.regex.Matcher mV = PATTERN_MAXV.matcher(text);
        if (mV.find()) {
            String valStr = mV.group(1);
            if (valStr != null && !valStr.isEmpty()) {
                prefs.edit().putString(PREF_EVCC_MAXV, valStr).apply();
                if (inpMaxV != null && !inpMaxV.hasFocus()) {
                    rootView.post(() -> inpMaxV.setText(valStr));
                }
            }
        }

        java.util.regex.Matcher mC = PATTERN_MAXC.matcher(text);
        if (mC.find()) {
            String valStr = mC.group(1);
            if (valStr != null && !valStr.isEmpty()) {
                prefs.edit().putString(PREF_EVCC_MAXC, valStr).apply();
                if (inpMaxC != null && !inpMaxC.hasFocus()) {
                    rootView.post(() -> inpMaxC.setText(valStr));
                }
            }
        }
    }

    public void resetUIState() {
        lastTelemetry = null;
        if (badgeState != null) badgeState.setText("State: STANDBY");
        if (badgeJ1772 != null) badgeJ1772.setText("J1772: DISCONNECTED");
        if (badgeChargeTimer != null) badgeChargeTimer.setText("⏱️ 00:00");
        if (chartTemperature != null) chartTemperature.clearData();

        // Charger 1
        if (c1StatusBadge != null) {
            c1StatusBadge.setText("STANDBY");
            c1StatusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
        }
        if (c1ValV != null) c1ValV.setText("0.0 V");
        if (c1ValA != null) c1ValA.setText("0.0 A");
        if (c1ValW != null) c1ValW.setText("0 W");
        if (c1ValWh != null) c1ValWh.setText("0 Wh");
        if (c1ValTmp != null) {
            c1ValTmp.setText("0.0 °C");
            c1ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
        }
        updateFaultPill(c1FaultRxerr, false);
        updateFaultPill(c1FaultHwfail, false);
        updateFaultPill(c1FaultOvertemp, false);
        updateFaultPill(c1FaultNotCharging, false);
        updateFaultPill(c1FaultInputErr, false);
        updateFaultPill(c1FaultPackErr, false);
        if (c1Chart != null) c1Chart.clearData();

        // Charger 2
        if (c2StatusBadge != null) {
            c2StatusBadge.setText("STANDBY");
            c2StatusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
        }
        if (c2ValV != null) c2ValV.setText("0.0 V");
        if (c2ValA != null) c2ValA.setText("0.0 A");
        if (c2ValW != null) c2ValW.setText("0 W");
        if (c2ValWh != null) c2ValWh.setText("0 Wh");
        if (c2ValTmp != null) {
            c2ValTmp.setText("0.0 °C");
            c2ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
        }
        updateFaultPill(c2FaultRxerr, false);
        updateFaultPill(c2FaultHwfail, false);
        updateFaultPill(c2FaultOvertemp, false);
        updateFaultPill(c2FaultNotCharging, false);
        updateFaultPill(c2FaultInputErr, false);
        updateFaultPill(c2FaultPackErr, false);
        if (c2Chart != null) c2Chart.clearData();

        updateChargerCardVisibility();

        // Reset preset buttons highlight if not in simulation mode
        if (!client.isSimulationMode()) {
            for (Button btn : scButtons) {
                if (btn != null) {
                    btn.setBackgroundColor(Color.parseColor("#1F223A"));
                    btn.setTextColor(Color.parseColor("#E2E8F0"));
                }
            }
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String status) {
        this.lastConnected = connected;
        this.lastConnectionStatus = status;
        updateBadgeIpUI(connected, status);
        if (!connected && !client.isSimulationMode()) {
            resetUIState();
        }
    }

    public void onTabActivated() {
        refreshGatewayConfig();
        updateChargerCardVisibility();
        if (badgeIp != null && lastConnectionStatus != null && !lastConnectionStatus.isEmpty()) {
            updateBadgeIpUI(lastConnected, lastConnectionStatus);
        }
        if (lastTelemetry != null) {
            updateUIWithTelemetry(lastTelemetry);
        } else if (!client.isSimulationMode() && !client.isConnected()) {
            resetUIState();
        }
        if (termLogText != null) {
            termLogText.setText(terminalBuffer.toString());
            if (termScroll != null) {
                termScroll.post(() -> termScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
        if (c1Chart != null) c1Chart.postInvalidate();
        if (c2Chart != null) c2Chart.postInvalidate();
    }

    @Override
    public void onTelemetryReceived(EvccTelemetry telemetry) {
        if (telemetry == null) return;
        this.lastTelemetry = telemetry;

        // Auto-clear graphs on session transition into CHARGE
        if (!"CHARGE".equalsIgnoreCase(lastEvccState) && "CHARGE".equalsIgnoreCase(telemetry.state)) {
            if (c1Chart != null) c1Chart.clearData();
            if (c2Chart != null) c2Chart.clearData();
            if (chartTemperature != null) chartTemperature.clearData();
        }
        lastEvccState = telemetry.state;

        ChargerTelemetry c1 = telemetry.charger1;
        ChargerTelemetry c2 = telemetry.charger2;

        // Record telemetry data points to chart histories only when actively charging
        if (c1 != null && c1Chart != null && (c1.active || c1.current > 0.2f)) {
            c1Chart.addDataPoint(c1.voltage, c1.current);
        }

        if (c2 != null && c2Chart != null && c2.active && c2.current > 0.2f) {
            c2Chart.addDataPoint(c2.voltage, c2.current);
        } else if (c2Chart != null && (c2 == null || !c2.active)) {
            c2Chart.clearData();
        }

        // Feed live temperatures to temperature histogram
        if (chartTemperature != null && (c1 != null || c2 != null)) {
            float t1 = (c1 != null && c1.active) ? c1.temperature : 0f;
            float t2 = (c2 != null && c2.active) ? c2.temperature : 0f;
            chartTemperature.addDataPoint(t1, t2);
        }

        // Charge Session Tracking: Trips Table line & MQTT upload
        boolean activelyCharging = ("CHARGE".equalsIgnoreCase(telemetry.state)) ||
                (c1 != null && c1.current > 0.5f) || (c2 != null && c2.current > 0.5f);

        float currentWh = (c1 != null ? c1.wattHours : 0f) + (c2 != null ? c2.wattHours : 0f);
        if (c1 != null && c1.temperature > peakTempC1) peakTempC1 = c1.temperature;
        if (c2 != null && c2.temperature > peakTempC2) peakTempC2 = c2.temperature;

        if (!isCurrentlyCharging && activelyCharging) {
            isCurrentlyCharging = true;
            chargeSessionStartMs = System.currentTimeMillis();
            peakTempC1 = c1 != null ? c1.temperature : 0f;
            peakTempC2 = c2 != null ? c2.temperature : 0f;
            if (chartTemperature != null) chartTemperature.clearData();
        } else if (isCurrentlyCharging && !activelyCharging) {
            // Charging session completed
            isCurrentlyCharging = false;
        }
        if (activelyCharging && currentWh > 0f) {
            lastTotalDeliveredWh = currentWh;
        }

        // Only touch UI widgets when the Charging Tab is actually visible
        if (rootView == null || !rootView.isShown()) return;

        updateUIWithTelemetry(telemetry);
    }

    private void updateUIWithTelemetry(EvccTelemetry telemetry) {
        // State Badges
        if (badgeState != null) badgeState.setText("State: " + getFriendlyStatus(telemetry));
        if (badgeJ1772 != null) badgeJ1772.setText("J1772: " + telemetry.j1772);

        // Charge Session Timer
        if (badgeChargeTimer != null) {
            long sec = telemetry.sessionSec;
            if (sec > 0) {
                long hrs = sec / 3600;
                long mins = (sec % 3600) / 60;
                long secs = sec % 60;
                if (hrs > 0) {
                    badgeChargeTimer.setText(String.format(Locale.US, "⏱️ %02d:%02d:%02d", hrs, mins, secs));
                } else {
                    badgeChargeTimer.setText(String.format(Locale.US, "⏱️ %02d:%02d", mins, secs));
                }
            } else {
                badgeChargeTimer.setText("⏱️ 00:00");
            }
        }

        // Charger 1
        ChargerTelemetry c1 = telemetry.charger1;
        if (c1 != null) {
            if (c1ValV != null) c1ValV.setText(String.format(Locale.US, "%.1f V", (c1.active || c1.voltage > 20.0f) ? c1.voltage : 0.0f));
            if (c1ValA != null) c1ValA.setText(String.format(Locale.US, "%.1f A", c1.active ? c1.current : 0.0f));
            if (c1ValW != null) c1ValW.setText(String.format(Locale.US, "%.0f W", c1.active ? c1.power : 0.0f));
            if (c1ValWh != null) c1ValWh.setText(String.format(Locale.US, "%.0f Wh", c1.active ? c1.wattHours : 0.0f));
            if (c1ValTmp != null) {
                if (c1.active) {
                    c1ValTmp.setText(String.format(Locale.US, "%.1f °C", c1.temperature));
                    if (c1.temperature >= 60f) {
                        c1ValTmp.setTextColor(Color.parseColor("#EF4444"));
                    } else if (c1.temperature >= 53f) {
                        c1ValTmp.setTextColor(Color.parseColor("#F59E0B"));
                    } else {
                        c1ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                    }
                } else {
                    c1ValTmp.setText("0.0 °C");
                    c1ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                }
            }

            if (c1StatusBadge != null) {
                if (c1.active && c1.current > 0.2f) {
                    c1StatusBadge.setText("CHARGING");
                    c1StatusBadge.setTextColor(Color.parseColor("#10B981"));
                } else {
                    c1StatusBadge.setText("STANDBY");
                    c1StatusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
                }
            }

            // Actively charging chargers are never "not charging"
            if (c1.active && c1.current > 0.5f) c1.notCharging = false;

            updateFaultPill(c1FaultRxerr, c1.active && c1.rxerr);
            updateFaultPill(c1FaultHwfail, c1.active && c1.hwfail);
            updateFaultPill(c1FaultOvertemp, c1.active && c1.overtemp);
            updateFaultPill(c1FaultNotCharging, c1.active && c1.notCharging);
            updateFaultPill(c1FaultInputErr, c1.active && c1.inputVoltageErr);
            updateFaultPill(c1FaultPackErr, c1.active && c1.packVoltageErr);
        }

        // Charger 2
        ChargerTelemetry c2 = telemetry.charger2;
        if (c2 != null) {
            if (c2ValV != null) c2ValV.setText(String.format(Locale.US, "%.1f V", (c2.active || c2.voltage > 20.0f) ? c2.voltage : 0.0f));
            if (c2ValA != null) c2ValA.setText(String.format(Locale.US, "%.1f A", c2.active ? c2.current : 0.0f));
            if (c2ValW != null) c2ValW.setText(String.format(Locale.US, "%.0f W", c2.active ? c2.power : 0.0f));
            if (c2ValWh != null) c2ValWh.setText(String.format(Locale.US, "%.0f Wh", c2.active ? c2.wattHours : 0.0f));
            if (c2ValTmp != null) {
                if (c2.active) {
                    c2ValTmp.setText(String.format(Locale.US, "%.1f °C", c2.temperature));
                    if (c2.temperature >= 60f) {
                        c2ValTmp.setTextColor(Color.parseColor("#EF4444"));
                    } else if (c2.temperature >= 53f) {
                        c2ValTmp.setTextColor(Color.parseColor("#F59E0B"));
                    } else {
                        c2ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                    }
                } else {
                    c2ValTmp.setText("0.0 °C");
                    c2ValTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                }
            }

            if (c2StatusBadge != null) {
                if (c2.active && c2.current > 0.2f) {
                    c2StatusBadge.setText("CHARGING");
                    c2StatusBadge.setTextColor(Color.parseColor("#10B981"));
                } else {
                    c2StatusBadge.setText("STANDBY");
                    c2StatusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
                }
            }

            // Actively charging chargers are never "not charging"
            if (c2.active && c2.current > 0.5f) c2.notCharging = false;

            updateFaultPill(c2FaultRxerr, c2.active && c2.rxerr);
            updateFaultPill(c2FaultHwfail, c2.active && c2.hwfail);
            updateFaultPill(c2FaultOvertemp, c2.active && c2.overtemp);
            updateFaultPill(c2FaultNotCharging, c2.active && c2.notCharging);
            updateFaultPill(c2FaultInputErr, c2.active && c2.inputVoltageErr);
            updateFaultPill(c2FaultPackErr, c2.active && c2.packVoltageErr);
        }

        // Dynamic Charger Card Visibility:
        // Keep the number of charger boxes as were active at the last charging session
        if ("CHARGE".equalsIgnoreCase(telemetry.state)) {
            if (c2 != null && c2.active) {
                persistActiveChargers(2);
            } else if (c1 != null && c1.active && c1.current > 0.5f) {
                persistActiveChargers(1);
            }
        }
        updateChargerCardVisibility();

        // Update MaxV and MaxC if telemetry contains them
        if (telemetry.maxv > 0 && inpMaxV != null && !inpMaxV.hasFocus()) {
            String vStr = String.format(Locale.US, "%.1f", telemetry.maxv);
            if (!vStr.equals(inpMaxV.getText().toString())) {
                inpMaxV.setText(vStr);
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_EVCC_MAXV, vStr).apply();
            }
        }
        if (telemetry.maxc > 0 && inpMaxC != null && !inpMaxC.hasFocus()) {
            String cStr = String.format(Locale.US, "%.1f", telemetry.maxc);
            if (!cStr.equals(inpMaxC.getText().toString())) {
                inpMaxC.setText(cStr);
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_EVCC_MAXC, cStr).apply();
            }
        }

        // Update Governor UI
        if (telemetry.governor != null) {
            updateGovernorUI(telemetry.governor);
        }
    }

    private void updateGovernorUI(com.thunderstruck.evcc.monitor.model.ThermalGovernorTelemetry gov) {
        if (gov == null) return;

        if (switchGovernor != null && switchGovernor.isChecked() != gov.enabled) {
            isUpdatingGovernorSwitch = true;
            switchGovernor.setChecked(gov.enabled);
            isUpdatingGovernorSwitch = false;
        }

        if (!gov.enabled) {
            if (badgeGovernor != null) {
                badgeGovernor.setText("\u26AA Gov: OFF");
                badgeGovernor.setTextColor(Color.parseColor("#9CA3AF"));
                badgeGovernor.setBackgroundColor(Color.parseColor("#1F293D"));
            }
            if (lblGovDetail != null) {
                lblGovDetail.setText("Disabled | Manual Control");
                lblGovDetail.setTextColor(Color.parseColor("#9CA3AF"));
            }
            if (bannerThermalDerate != null) bannerThermalDerate.setVisibility(View.GONE);
        } else if (gov.isDerated) {
            boolean isCritical = gov.deratePercent <= 50;
            if (badgeGovernor != null) {
                badgeGovernor.setText(String.format(Locale.US, "\u26A0\uFE0F Gov: %d%%", gov.deratePercent));
                badgeGovernor.setTextColor(isCritical ? Color.parseColor("#EF4444") : Color.parseColor("#F59E0B"));
                badgeGovernor.setBackgroundColor(isCritical ? Color.parseColor("#450A0A") : Color.parseColor("#451A03"));
            }
            if (lblGovDetail != null) {
                lblGovDetail.setText(String.format(Locale.US, "\u26A0\uFE0F %.1fA / %.1fA (%d%%) | Peak %s %.0f\u00B0C",
                        gov.activeMaxc, gov.baselineMaxc, gov.deratePercent, gov.hottestCharger, gov.peakTemp));
                lblGovDetail.setTextColor(Color.parseColor("#F59E0B"));
            }
            if (bannerThermalDerate != null && !bannerDismissed) {
                bannerThermalDerate.setVisibility(View.VISIBLE);
                if (bannerDerateDesc != null) {
                    bannerDerateDesc.setText(String.format(Locale.US,
                            "Throttled %.1fA -> %.1fA (-%d%%) | %s at %.0f\u00B0C (%s)",
                            gov.baselineMaxc, gov.activeMaxc, 100 - gov.deratePercent, gov.hottestCharger, gov.peakTemp, gov.statusText));
                }
            }
        } else {
            if (badgeGovernor != null) {
                badgeGovernor.setText("\uD83D\uDEE1\uFE0F Gov: OPTIMAL");
                badgeGovernor.setTextColor(Color.parseColor("#10B981"));
                badgeGovernor.setBackgroundColor(Color.parseColor("#1F293D"));
            }
            if (lblGovDetail != null) {
                lblGovDetail.setText(String.format(Locale.US, "Active: %.1fA (100%%) | Peak %.0f\u00B0C",
                        gov.baselineMaxc, gov.peakTemp));
                lblGovDetail.setTextColor(Color.parseColor("#9CA3AF"));
            }
            if (bannerThermalDerate != null) bannerThermalDerate.setVisibility(View.GONE);
            bannerDismissed = false; // Reset dismiss flag when back in optimal zone
        }
    }

    private void updateFaultPill(TextView pill, boolean isFault) {
        if (pill == null) return;
        if (isFault) {
            pill.setTextColor(Color.WHITE);
            pill.setBackgroundColor(themeNegativeColor);
        } else {
            pill.setTextColor(themeTitleColor);
            pill.setBackgroundColor(Color.parseColor("#1E293B"));
        }
    }

    @Override
    public void onRawLineReceived(String line, boolean isTx) {
        terminalBuffer.append(line);
        if (terminalBuffer.length() > MAX_TERMINAL_BUFFER_CHARS) {
            terminalBuffer.delete(0, terminalBuffer.length() - (MAX_TERMINAL_BUFFER_CHARS / 2));
        }

        parseAndStoreEvccConfigParams(line);

        if (rootView != null && rootView.isShown() && termLogText != null) {
            termLogText.setText(terminalBuffer.toString());
            if (termScroll != null) {
                termScroll.post(() -> termScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    }

    @Override
    public void onQueryResponseReceived(String query, String text) {
        terminalBuffer.append("\n--- [EVCC Query: ").append(query).append("] ---\n").append(text).append("\n");
        parseAndStoreEvccConfigParams(text);

        if (rootView != null && rootView.isShown() && termLogText != null) {
            termLogText.setText(terminalBuffer.toString());
            if (termScroll != null) {
                termScroll.post(() -> termScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }

        ScrollView sv = new ScrollView(context);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(context);
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setPadding(28, 20, 28, 20);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setHorizontallyScrolling(true);
        tv.setTextIsSelectable(true);
        hsv.addView(tv);
        sv.addView(hsv);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("📋 EVCC: " + query)
                .setView(sv)
                .setPositiveButton("Close", null)
                .create();
        dialog.show();

        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int targetWidth = Math.min((int) (dm.widthPixels * 0.88), 1600);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showLogDialog() {
        String logs = EvccSessionLogger.getInstance().getRecentLogs(300);
        ScrollView sv = new ScrollView(context);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(context);
        TextView tv = new TextView(context);
        tv.setText(logs);
        tv.setPadding(28, 20, 28, 20);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setHorizontallyScrolling(true);
        tv.setTextIsSelectable(true);
        hsv.addView(tv);
        sv.addView(hsv);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("📜 EVCC Session Log (Tablet Storage)")
                .setView(sv)
                .setPositiveButton("Close", null)
                .setNegativeButton("Clear Log", (d, w) -> {
                    EvccSessionLogger.getInstance().clearLog();
                    Toast.makeText(context, "Tablet log cleared", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Copy", (d, w) -> {
                    try {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("EVCC Log", logs);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) {}
                })
                .create();
        dialog.show();

        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int targetWidth = Math.min((int) (dm.widthPixels * 0.88), 1600);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private String getFriendlyStatus(EvccTelemetry t) {
        if (t == null) return "STANDBY";
        String raw = t.state != null ? t.state.trim() : "";
        boolean c1Active = t.charger1 != null && (t.charger1.active || t.charger1.current > 0.2f);
        boolean c2Active = t.charger2 != null && (t.charger2.active || t.charger2.current > 0.2f);
        float totalKw = ((t.charger1 != null ? t.charger1.power : 0f) + (t.charger2 != null ? t.charger2.power : 0f)) / 1000.0f;
        float totalA = (t.charger1 != null ? t.charger1.current : 0f) + (t.charger2 != null ? t.charger2.current : 0f);

        if ("CHARGE".equalsIgnoreCase(raw) || c1Active || c2Active) {
            return String.format(Locale.US, "CHARGING (%.1f kW / %.1f A)", totalKw, totalA);
        }
        if ((t.charger1 != null && t.charger1.overtemp) || (t.charger2 != null && t.charger2.overtemp)) {
            return "PAUSED (OVERTEMP)";
        }
        if ("COMPLETE".equalsIgnoreCase(raw)) {
            return "CHARGE COMPLETE";
        }
        if (raw.equalsIgnoreCase("STANDBY") || raw.isEmpty() || raw.equalsIgnoreCase("UNKNOWN")) {
            if (t.j1772 != null && t.j1772.toLowerCase().contains("connected")) {
                return "READY (WAITING PILOT)";
            }
            return "STANDBY";
        }
        return raw.toUpperCase(Locale.US);
    }


}

