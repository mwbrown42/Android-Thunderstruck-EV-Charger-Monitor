package com.mikeland.thunderstruck.evcc.monitor.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.mikeland.thunderstruck.evcc.monitor.R;
import com.mikeland.thunderstruck.evcc.monitor.SettingsActivity;
import com.mikeland.thunderstruck.evcc.monitor.controller.EvccGatewayClient;
import com.mikeland.thunderstruck.evcc.monitor.controller.EvccSessionLogger;
import com.mikeland.thunderstruck.evcc.monitor.controller.EvccSimulatorEngine;
import com.mikeland.thunderstruck.evcc.monitor.model.ChargerTelemetry;
import com.mikeland.thunderstruck.evcc.monitor.model.EvccTelemetry;
import com.mikeland.thunderstruck.evcc.monitor.model.SessionDataPoint;
import com.mikeland.thunderstruck.evcc.monitor.model.ThermalGovernorTelemetry;

import org.json.JSONObject;

import java.util.Locale;

public class ChargingTabViewController implements EvccGatewayClient.EvccEventListener {

    private final Context context;
    private final View rootView;
    private final EvccGatewayClient client = EvccGatewayClient.getInstance();
    private final EvccSimulatorEngine simulator = EvccSimulatorEngine.getInstance();

    private EvccTelemetry lastTelemetry = null;
    private String lastEvccState = "";
    private boolean lastConnected = false;
    private String lastConnectionStatus = "";
    private final StringBuilder terminalBuffer = new StringBuilder();
    private static final int MAX_TERMINAL_BUFFER_CHARS = 10000;

    // Theme Colors
    private int themeTitleColor = Color.parseColor("#94A3B8");
    private int themeValueColor = Color.parseColor("#FFFFFF");
    private int themeIndicatorColor = Color.parseColor("#8B5CF6");
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

    // Charge Session Tracking
    private boolean isCurrentlyCharging = false;
    private long chargeSessionStartMs = 0;
    private float lastTotalDeliveredWh = 0f;

    // Thermal Governor Views
    private View bannerThermalDerate;
    private TextView bannerDerateTitle, bannerDerateDesc;
    private Button btnDismissDerateBanner;
    private TextView lblGovDetail;
    private Switch switchGovernor;
    private boolean bannerDismissed = false;
    private boolean isUpdatingGovernorSwitch = false;

    // CC/CV Dynamic Tapering Views
    private TextView badgeCccv;
    private View cardCccvGovernor;
    private TextView cccvValVpack, cccvValVcell, cccvValItarget, cccvValIactive, cccvPhaseBadge;
    private Button btnCccvSettings;
    private CccvCurveChartView cccvChartView;

    // Charger View Holder Supporting up to 4 Chargers Dynamically
    private static class ChargerViewHolder {
        View cardView;
        TextView titleLabel;
        TextView tagLabel;
        TextView statusBadge;
        TextView lblV, lblA, lblW, lblWh, lblTmp;
        TextView valV, valA, valW, valWh, valTmp;
        TextView faultRxerr, faultHwfail, faultOvertemp, faultNotCharging, faultInputErr, faultPackErr;
        ChargingChartView chart;
    }

    private final ChargerViewHolder[] chargers = new ChargerViewHolder[4];

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
        updateSimButtonUI(client.isSimulationMode());
        refreshGatewayConfig();
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
        refreshGatewayConfig();
        updateChargerCardVisibility();
    }

    public void onPause() {
        client.removeListener(this);
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

        // 4 Chargers
        int[] cardIds = {R.id.card_charger1, R.id.card_charger2, R.id.card_charger3, R.id.card_charger4};
        int[] titleIds = {R.id.c1_title_label, R.id.c2_title_label, R.id.c3_title_label, R.id.c4_title_label};
        int[] tagIds = {R.id.c1_tag_label, R.id.c2_tag_label, R.id.c3_tag_label, R.id.c4_tag_label};
        int[] statusBadgeIds = {R.id.c1_status_badge, R.id.c2_status_badge, R.id.c3_status_badge, R.id.c4_status_badge};
        int[] vLblIds = {R.id.c1_lbl_v, R.id.c2_lbl_v, R.id.c3_lbl_v, R.id.c4_lbl_v};
        int[] aLblIds = {R.id.c1_lbl_a, R.id.c2_lbl_a, R.id.c3_lbl_a, R.id.c4_lbl_a};
        int[] wLblIds = {R.id.c1_lbl_w, R.id.c2_lbl_w, R.id.c3_lbl_w, R.id.c4_lbl_w};
        int[] whLblIds = {R.id.c1_lbl_wh, R.id.c2_lbl_wh, R.id.c3_lbl_wh, R.id.c4_lbl_wh};
        int[] tmpLblIds = {R.id.c1_lbl_tmp, R.id.c2_lbl_tmp, R.id.c3_lbl_tmp, R.id.c4_lbl_tmp};
        int[] vValIds = {R.id.c1_val_v, R.id.c2_val_v, R.id.c3_val_v, R.id.c4_val_v};
        int[] aValIds = {R.id.c1_val_a, R.id.c2_val_a, R.id.c3_val_a, R.id.c4_val_a};
        int[] wValIds = {R.id.c1_val_w, R.id.c2_val_w, R.id.c3_val_w, R.id.c4_val_w};
        int[] whValIds = {R.id.c1_val_wh, R.id.c2_val_wh, R.id.c3_val_wh, R.id.c4_val_wh};
        int[] tmpValIds = {R.id.c1_val_tmp, R.id.c2_val_tmp, R.id.c3_val_tmp, R.id.c4_val_tmp};
        int[] rxerrIds = {R.id.c1_fault_rxerr, R.id.c2_fault_rxerr, R.id.c3_fault_rxerr, R.id.c4_fault_rxerr};
        int[] hwfailIds = {R.id.c1_fault_hwfail, R.id.c2_fault_hwfail, R.id.c3_fault_hwfail, R.id.c4_fault_hwfail};
        int[] overtempIds = {R.id.c1_fault_overtemp, R.id.c2_fault_overtemp, R.id.c3_fault_overtemp, R.id.c4_fault_overtemp};
        int[] notChgIds = {R.id.c1_fault_not_charging, R.id.c2_fault_not_charging, R.id.c3_fault_not_charging, R.id.c4_fault_not_charging};
        int[] inputErrIds = {R.id.c1_fault_input_voltage_err, R.id.c2_fault_input_voltage_err, R.id.c3_fault_input_voltage_err, R.id.c4_fault_input_voltage_err};
        int[] packErrIds = {R.id.c1_fault_pack_voltage_err, R.id.c2_fault_pack_voltage_err, R.id.c3_fault_pack_voltage_err, R.id.c4_fault_pack_voltage_err};
        int[] chartIds = {R.id.c1_chart, R.id.c2_chart, R.id.c3_chart, R.id.c4_chart};

        for (int i = 0; i < 4; i++) {
            ChargerViewHolder h = new ChargerViewHolder();
            h.cardView = rootView.findViewById(cardIds[i]);
            h.titleLabel = rootView.findViewById(titleIds[i]);
            h.tagLabel = rootView.findViewById(tagIds[i]);
            h.statusBadge = rootView.findViewById(statusBadgeIds[i]);
            h.lblV = rootView.findViewById(vLblIds[i]);
            h.lblA = rootView.findViewById(aLblIds[i]);
            h.lblW = rootView.findViewById(wLblIds[i]);
            h.lblWh = rootView.findViewById(whLblIds[i]);
            h.lblTmp = rootView.findViewById(tmpLblIds[i]);
            h.valV = rootView.findViewById(vValIds[i]);
            h.valA = rootView.findViewById(aValIds[i]);
            h.valW = rootView.findViewById(wValIds[i]);
            h.valWh = rootView.findViewById(whValIds[i]);
            h.valTmp = rootView.findViewById(tmpValIds[i]);
            h.faultRxerr = rootView.findViewById(rxerrIds[i]);
            h.faultHwfail = rootView.findViewById(hwfailIds[i]);
            h.faultOvertemp = rootView.findViewById(overtempIds[i]);
            h.faultNotCharging = rootView.findViewById(notChgIds[i]);
            h.faultInputErr = rootView.findViewById(inputErrIds[i]);
            h.faultPackErr = rootView.findViewById(packErrIds[i]);
            h.chart = rootView.findViewById(chartIds[i]);
            chargers[i] = h;
        }

        // Sim Presets
        int[] scIds = {R.id.sc_btn_0, R.id.sc_btn_1, R.id.sc_btn_2,
                R.id.sc_btn_3, R.id.sc_btn_4, R.id.sc_btn_5,
                R.id.sc_btn_6, R.id.sc_btn_7};
        for (int i = 0; i < scIds.length; i++) {
            scButtons[i] = rootView.findViewById(scIds[i]);
        }
        simSeekC1V = rootView.findViewById(R.id.sim_seek_c1_v);
        simSeekC1A = rootView.findViewById(R.id.sim_seek_c1_a);
        simLblC1V = rootView.findViewById(R.id.sim_lbl_c1_v);
        simLblC1A = rootView.findViewById(R.id.sim_lbl_c1_a);

        // Parameters & Traces
        btnTrCan = rootView.findViewById(R.id.btn_trace_can);
        btnTrState = rootView.findViewById(R.id.btn_trace_state);
        btnTrChg = rootView.findViewById(R.id.btn_trace_charger);
        btnTrOff = rootView.findViewById(R.id.btn_trace_off);
        inpMaxV = rootView.findViewById(R.id.inp_maxv);
        inpMaxC = rootView.findViewById(R.id.inp_maxc);
        btnSetMaxV = rootView.findViewById(R.id.btn_set_maxv);
        btnSetMaxC = rootView.findViewById(R.id.btn_set_maxc);

        // Terminal
        termScroll = rootView.findViewById(R.id.term_scroll);
        termLogText = rootView.findViewById(R.id.term_log_text);
        termInput = rootView.findViewById(R.id.term_input);
        btnTermSend = rootView.findViewById(R.id.btn_term_send);
        btnClearTerminal = rootView.findViewById(R.id.btn_clear_terminal);
        btnDiagUsb = rootView.findViewById(R.id.btn_diag_usb);
        btnViewEvccLog = rootView.findViewById(R.id.btn_view_evcc_log);

        // CC/CV Dynamic Tapering
        badgeCccv = rootView.findViewById(R.id.badge_cccv);
        cardCccvGovernor = rootView.findViewById(R.id.card_cccv_governor);
        cccvValVpack = rootView.findViewById(R.id.cccv_val_vpack);
        cccvValVcell = rootView.findViewById(R.id.cccv_val_vcell);
        cccvValItarget = rootView.findViewById(R.id.cccv_val_itarget);
        cccvValIactive = rootView.findViewById(R.id.cccv_val_iactive);
        cccvPhaseBadge = rootView.findViewById(R.id.cccv_phase_badge);
        btnCccvSettings = rootView.findViewById(R.id.btn_cccv_settings);
        cccvChartView = rootView.findViewById(R.id.cccv_chart_view);

        if (btnCccvSettings != null) {
            btnCccvSettings.setOnClickListener(v -> showCccvSettingsDialog());
        }
        if (badgeCccv != null) {
            badgeCccv.setOnClickListener(v -> showCccvSettingsDialog());
        }

        resetUIState();
    }

    public void applyTheme() {
        themeTitleColor = Color.parseColor("#94A3B8");
        themeValueColor = Color.parseColor("#FFFFFF");
        themeIndicatorColor = Color.parseColor("#8B5CF6");
        themeNegativeColor = Color.parseColor("#EF4444");
        themeTickColor = Color.parseColor("#374151");

        setTextViewColor(rootView.findViewById(R.id.title_gateway_header), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_sim_presets), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_sim_c1_v_title), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_sim_c1_a_title), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_params_traces), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_maxv_param), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.lbl_maxc_param), themeTitleColor);
        setTextViewColor(rootView.findViewById(R.id.title_terminal), themeTitleColor);
        setTextViewColor(simLblC1V, themeValueColor);
        setTextViewColor(simLblC1A, themeValueColor);

        for (int i = 0; i < 4; i++) {
            ChargerViewHolder h = chargers[i];
            if (h == null) continue;
            setTextViewColor(h.titleLabel, themeTitleColor);
            setTextViewColor(h.lblV, themeTitleColor);
            setTextViewColor(h.lblA, themeTitleColor);
            setTextViewColor(h.lblW, themeTitleColor);
            setTextViewColor(h.lblWh, themeTitleColor);
            setTextViewColor(h.lblTmp, themeTitleColor);
            setTextViewColor(h.valV, themeValueColor);
            setTextViewColor(h.valA, themeValueColor);
            setTextViewColor(h.valW, themeValueColor);
            setTextViewColor(h.valWh, themeValueColor);
            setTextViewColor(h.valTmp, themeValueColor);
            if (h.chart != null) {
                h.chart.applyTheme(themeFont, themeTitleColor, themeValueColor, themeIndicatorColor, themeNegativeColor, themeTickColor);
            }
        }

        if (btnSetMaxV != null) btnSetMaxV.setBackgroundColor(themeIndicatorColor);
        if (btnSetMaxC != null) btnSetMaxC.setBackgroundColor(themeIndicatorColor);
        if (btnTermSend != null) btnTermSend.setBackgroundColor(themeIndicatorColor);
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
                for (ChargerViewHolder h : chargers) {
                    if (h != null && h.chart != null) h.chart.clearData();
                }
                if (!newMode) {
                    resetUIState();
                    refreshGatewayConfig();
                }
            });
        }

        View btnShow = rootView.findViewById(R.id.btn_show_status);
        if (btnShow != null) btnShow.setOnClickListener(v -> client.sendQuery("show"));

        View btnCfg = rootView.findViewById(R.id.btn_show_config);
        if (btnCfg != null) btnCfg.setOnClickListener(v -> client.sendQuery("config"));

        View btnHist = rootView.findViewById(R.id.btn_show_history);
        if (btnHist != null) btnHist.setOnClickListener(v -> client.sendQuery("history"));

        View btnReset = rootView.findViewById(R.id.btn_reset_graph);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                for (ChargerViewHolder h : chargers) {
                    if (h != null && h.chart != null) h.chart.clearData();
                }
                if (chartTemperature != null) chartTemperature.clearData();
            });
        }

        for (int i = 0; i < scButtons.length; i++) {
            final int idx = i;
            if (scButtons[i] != null) {
                scButtons[i].setOnClickListener(v -> {
                    if (!client.isSimulationMode()) {
                        client.setSimulationMode(true);
                        updateSimButtonUI(true);
                    }
                    simulator.applyScenario(EvccSimulatorEngine.Scenario.values()[idx]);
                    updatePresetButtonsUI(idx);
                });
            }
        }

        if (simSeekC1V != null) {
            simSeekC1V.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float v = progress;
                        if (simLblC1V != null) simLblC1V.setText(String.format(Locale.US, "%.1fV", v));
                        float a = simSeekC1A != null ? simSeekC1A.getProgress() : 20.0f;
                        simulator.setChargerValues(40, v, a, 38.0f);
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
                        float v = simSeekC1V != null ? simSeekC1V.getProgress() : 142.0f;
                        simulator.setChargerValues(40, v, a, 38.0f);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnTrCan != null) btnTrCan.setOnClickListener(v -> client.sendTraceToggle("can"));
        if (btnTrState != null) btnTrState.setOnClickListener(v -> client.sendTraceToggle("state"));
        if (btnTrChg != null) btnTrChg.setOnClickListener(v -> client.sendTraceToggle("charger"));
        if (btnTrOff != null) btnTrOff.setOnClickListener(v -> client.sendTraceToggle("off"));

        if (btnSetMaxV != null) {
            btnSetMaxV.setOnClickListener(v -> {
                if (inpMaxV == null) return;
                String val = inpMaxV.getText().toString().trim();
                if (!val.isEmpty()) {
                    try {
                        float fVal = Float.parseFloat(val);
                        client.sendParamSet("maxv", fVal);
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_EVCC_MAXV, val).apply();
                        Toast.makeText(context, "Set maxv -> " + val + "V", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }
            });
        }

        if (btnSetMaxC != null) {
            btnSetMaxC.setOnClickListener(v -> {
                if (inpMaxC == null) return;
                String val = inpMaxC.getText().toString().trim();
                if (!val.isEmpty()) {
                    try {
                        float fVal = Float.parseFloat(val);
                        client.sendParamSet("maxc", fVal);
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_EVCC_MAXC, val).apply();
                        Toast.makeText(context, "Set maxc -> " + val + "A", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }
            });
        }

        if (switchGovernor != null) {
            switchGovernor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isUpdatingGovernorSwitch) {
                    client.sendGovernorToggle(isChecked);
                }
            });
        }

        if (btnDismissDerateBanner != null) {
            btnDismissDerateBanner.setOnClickListener(v -> {
                bannerDismissed = true;
                if (bannerThermalDerate != null) bannerThermalDerate.setVisibility(View.GONE);
            });
        }

        if (btnTermSend != null) {
            btnTermSend.setOnClickListener(v -> {
                if (termInput == null) return;
                String cmd = termInput.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    client.sendRawCommand(cmd);
                    termInput.setText("");
                }
            });
        }

        if (btnClearTerminal != null) {
            btnClearTerminal.setOnClickListener(v -> {
                terminalBuffer.setLength(0);
                if (termLogText != null) termLogText.setText("");
            });
        }

        if (btnDiagUsb != null) {
            btnDiagUsb.setOnClickListener(v -> client.sendRawCommand("usb"));
        }

        if (btnViewEvccLog != null) {
            btnViewEvccLog.setOnClickListener(v -> showLogDialog());
        }
    }

    public void refreshGatewayConfig() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String ip = prefs.getString("evccGatewayIpKey", "");
        String port = prefs.getString("evccGatewayPortKey", "80");
        if (!ip.isEmpty()) {
            client.setGatewayConfig(ip, port);
        }

        if (inpMaxV != null && inpMaxV.getText().toString().isEmpty()) {
            inpMaxV.setText(prefs.getString(PREF_EVCC_MAXV, "142.0"));
        }
        if (inpMaxC != null && inpMaxC.getText().toString().isEmpty()) {
            inpMaxC.setText(prefs.getString(PREF_EVCC_MAXC, "80.0"));
        }
    }

    private int getPersistedActiveChargers() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_LAST_ACTIVE_CHARGERS, 2);
    }

    private void persistActiveChargers(int count) {
        if (count < 1) count = 1;
        if (count > 4) count = 4;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(PREF_LAST_ACTIVE_CHARGERS, 2) != count) {
            prefs.edit().putInt(PREF_LAST_ACTIVE_CHARGERS, count).apply();
            updateChargerCardVisibility();
        }
    }

    private void updateChargerCardVisibility() {
        if (rootView == null) return;
        int count = getPersistedActiveChargers();
        for (int i = 0; i < 4; i++) {
            ChargerViewHolder h = chargers[i];
            if (h != null && h.cardView != null) {
                boolean show = (i < count);
                h.cardView.setVisibility(show ? View.VISIBLE : View.GONE);
                if (!show && h.chart != null) {
                    h.chart.clearData();
                }
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

        for (int i = 0; i < 4; i++) {
            ChargerViewHolder h = chargers[i];
            if (h == null) continue;
            if (h.statusBadge != null) {
                h.statusBadge.setText("STANDBY");
                h.statusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
            }
            if (h.valV != null) h.valV.setText("0.0 V");
            if (h.valA != null) h.valA.setText("0.0 A");
            if (h.valW != null) h.valW.setText("0 W");
            if (h.valWh != null) h.valWh.setText("0 Wh");
            if (h.valTmp != null) {
                h.valTmp.setText("0.0 °C");
                h.valTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
            }
            updateFaultPill(h.faultRxerr, false);
            updateFaultPill(h.faultHwfail, false);
            updateFaultPill(h.faultOvertemp, false);
            updateFaultPill(h.faultNotCharging, false);
            updateFaultPill(h.faultInputErr, false);
            updateFaultPill(h.faultPackErr, false);
            if (h.chart != null) h.chart.clearData();
        }

        updateChargerCardVisibility();

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
        for (ChargerViewHolder h : chargers) {
            if (h != null && h.chart != null) h.chart.postInvalidate();
        }
    }

    @Override
    public void onTelemetryReceived(EvccTelemetry telemetry) {
        if (telemetry == null) return;
        this.lastTelemetry = telemetry;

        // Auto-clear graphs on session transition into CHARGE
        if (!"CHARGE".equalsIgnoreCase(lastEvccState) && "CHARGE".equalsIgnoreCase(telemetry.state)) {
            for (ChargerViewHolder h : chargers) {
                if (h != null && h.chart != null) h.chart.clearData();
            }
            if (chartTemperature != null) chartTemperature.clearData();
        }
        lastEvccState = telemetry.state;

        // Feed live telemetry points to all 4 charger charts
        for (int i = 0; i < 4; i++) {
            ChargerTelemetry ch = telemetry.getCharger(i);
            ChargerViewHolder h = chargers[i];
            if (ch != null && h != null && h.chart != null) {
                if (ch.active || ch.current > 0.2f) {
                    h.chart.addDataPoint(ch.voltage, ch.current);
                } else if (!ch.active) {
                    h.chart.clearData();
                }
            }
        }

        // Feed live temperatures to temperature chart
        if (chartTemperature != null) {
            float t1 = (telemetry.charger1 != null && telemetry.charger1.active) ? telemetry.charger1.temperature : 0f;
            float t2 = (telemetry.charger2 != null && telemetry.charger2.active) ? telemetry.charger2.temperature : 0f;
            float t3 = (telemetry.charger3 != null && telemetry.charger3.active) ? telemetry.charger3.temperature : 0f;
            float t4 = (telemetry.charger4 != null && telemetry.charger4.active) ? telemetry.charger4.temperature : 0f;
            chartTemperature.addDataPoint(t1, t2, t3, t4);
        }

        boolean activelyCharging = ("CHARGE".equalsIgnoreCase(telemetry.state)) ||
                (telemetry.charger1 != null && telemetry.charger1.current > 0.5f) ||
                (telemetry.charger2 != null && telemetry.charger2.current > 0.5f) ||
                (telemetry.charger3 != null && telemetry.charger3.current > 0.5f) ||
                (telemetry.charger4 != null && telemetry.charger4.current > 0.5f);

        if (!isCurrentlyCharging && activelyCharging) {
            isCurrentlyCharging = true;
            chargeSessionStartMs = System.currentTimeMillis();
            if (chartTemperature != null) chartTemperature.clearData();
        } else if (isCurrentlyCharging && !activelyCharging) {
            isCurrentlyCharging = false;
        }

        if (rootView == null || !rootView.isShown()) return;
        updateUIWithTelemetry(telemetry);
    }

    private void updateUIWithTelemetry(EvccTelemetry telemetry) {
        if (badgeState != null) badgeState.setText("State: " + getFriendlyStatus(telemetry));
        if (badgeJ1772 != null) badgeJ1772.setText("J1772: " + telemetry.j1772);

        if (badgeChargeTimer != null) {
            if (telemetry.sessionSec > 0) {
                long m = telemetry.sessionSec / 60;
                long s = telemetry.sessionSec % 60;
                badgeChargeTimer.setText(String.format(Locale.US, "⏱️ %02d:%02d", m, s));
            } else if (isCurrentlyCharging && chargeSessionStartMs > 0) {
                long elapsed = (System.currentTimeMillis() - chargeSessionStartMs) / 1000;
                long m = elapsed / 60;
                long s = elapsed % 60;
                badgeChargeTimer.setText(String.format(Locale.US, "⏱️ %02d:%02d", m, s));
            } else {
                badgeChargeTimer.setText("⏱️ 00:00");
            }
        }

        for (int i = 0; i < 4; i++) {
            ChargerTelemetry ch = telemetry.getCharger(i);
            ChargerViewHolder h = chargers[i];
            if (ch == null || h == null) continue;

            if (h.valV != null) h.valV.setText(String.format(Locale.US, "%.1f V", (ch.active || ch.voltage > 20.0f) ? ch.voltage : 0.0f));
            if (h.valA != null) h.valA.setText(String.format(Locale.US, "%.1f A", ch.active ? ch.current : 0.0f));
            if (h.valW != null) h.valW.setText(String.format(Locale.US, "%.0f W", ch.active ? ch.power : 0.0f));
            if (h.valWh != null) h.valWh.setText(String.format(Locale.US, "%.0f Wh", ch.active ? ch.wattHours : 0.0f));
            if (h.valTmp != null) {
                if (ch.active) {
                    h.valTmp.setText(String.format(Locale.US, "%.1f °C", ch.temperature));
                    if (ch.temperature >= 82f) {
                        h.valTmp.setTextColor(Color.parseColor("#EF4444")); // Red near 85°C ceiling
                    } else if (ch.temperature >= 66f) {
                        h.valTmp.setTextColor(Color.parseColor("#F59E0B")); // Amber in throttling band
                    } else {
                        h.valTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                    }
                } else {
                    h.valTmp.setText("0.0 °C");
                    h.valTmp.setTextColor(themeValueColor != 0 ? themeValueColor : Color.WHITE);
                }
            }

            ThermalGovernorTelemetry gov = telemetry.governor;
            ThermalGovernorTelemetry.ChargerGovernorInfo chGov = (gov != null && gov.chargers != null && i < gov.chargers.length) ? gov.chargers[i] : null;

            if (h.statusBadge != null) {
                if (ch.active) {
                    if (ch.overtemp) {
                        h.statusBadge.setText("OVERTEMP");
                        h.statusBadge.setTextColor(Color.parseColor("#EF4444"));
                    } else if (gov != null && gov.enabled && chGov != null && chGov.isDerated) {
                        h.statusBadge.setText(String.format(Locale.US, "DERATED %d%%", Math.round(chGov.scale * 100)));
                        h.statusBadge.setTextColor(Color.parseColor("#F59E0B"));
                    } else if (ch.current > 0.5f) {
                        h.statusBadge.setText("CHARGING");
                        h.statusBadge.setTextColor(Color.parseColor("#10B981"));
                    } else {
                        h.statusBadge.setText("STANDBY");
                        h.statusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
                    }
                } else {
                    h.statusBadge.setText("STANDBY");
                    h.statusBadge.setTextColor(themeTitleColor != 0 ? themeTitleColor : Color.parseColor("#9CA3AF"));
                }
            }

            if (ch.active && ch.current > 0.5f) ch.notCharging = false;

            updateFaultPill(h.faultRxerr, ch.active && ch.rxerr);
            updateFaultPill(h.faultHwfail, ch.active && ch.hwfail);
            updateFaultPill(h.faultOvertemp, ch.active && ch.overtemp);
            updateFaultPill(h.faultNotCharging, ch.active && ch.notCharging);
            updateFaultPill(h.faultInputErr, ch.active && ch.inputVoltageErr);
            updateFaultPill(h.faultPackErr, ch.active && ch.packVoltageErr);
        }

        // Dynamic card visibility based on active chargers detected
        int count = telemetry.getActiveChargerCount();
        persistActiveChargers(count);
        updateChargerCardVisibility();

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

        if (telemetry.governor != null) {
            updateGovernorUI(telemetry.governor);
        }

        if (telemetry.cccv != null) {
            updateCccvUI(telemetry.cccv);
        }
    }

    private void updateGovernorUI(ThermalGovernorTelemetry gov) {
        if (gov == null) return;

        if (switchGovernor != null && switchGovernor.isChecked() != gov.enabled) {
            isUpdatingGovernorSwitch = true;
            switchGovernor.setChecked(gov.enabled);
            isUpdatingGovernorSwitch = false;
        }

        if (!gov.enabled) {
            if (badgeGovernor != null) {
                badgeGovernor.setText("⚪ Gov: OFF");
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
                badgeGovernor.setText(String.format(Locale.US, "⚠️ Gov: %d%%", gov.deratePercent));
                badgeGovernor.setTextColor(isCritical ? Color.parseColor("#EF4444") : Color.parseColor("#F59E0B"));
                badgeGovernor.setBackgroundColor(isCritical ? Color.parseColor("#450A0A") : Color.parseColor("#451A03"));
            }
            if (lblGovDetail != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "⚠️ %.1fA / %.1fA (%d%%) | Peak %s %.0f°C",
                        gov.activeMaxc, gov.baselineMaxc, gov.deratePercent, gov.hottestCharger, gov.peakTemp));
                if (gov.chargers != null) {
                    sb.append(" (");
                    boolean first = true;
                    for (int i = 0; i < 4; i++) {
                        if (gov.chargers[i] != null && gov.chargers[i].temp > 0) {
                            if (!first) sb.append(", ");
                            sb.append(String.format(Locale.US, "C%d: %.0f°C", i + 1, gov.chargers[i].temp));
                            if (gov.chargers[i].isDerated) {
                                sb.append(String.format(Locale.US, " [%d%%]", Math.round(gov.chargers[i].scale * 100)));
                            }
                            first = false;
                        }
                    }
                    sb.append(")");
                }
                lblGovDetail.setText(sb.toString());
                lblGovDetail.setTextColor(Color.parseColor("#F59E0B"));
            }
            if (bannerThermalDerate != null && !bannerDismissed) {
                bannerThermalDerate.setVisibility(View.VISIBLE);
                if (bannerDerateDesc != null) {
                    bannerDerateDesc.setText(String.format(Locale.US,
                            "Throttled %.1fA -> %.1fA (-%d%%) by %s at %.0f°C (%s)",
                            gov.baselineMaxc, gov.activeMaxc, 100 - gov.deratePercent, gov.hottestCharger, gov.peakTemp, gov.statusText));
                }
            }
        } else {
            if (badgeGovernor != null) {
                badgeGovernor.setText("🛡️ Gov: OPTIMAL");
                badgeGovernor.setTextColor(Color.parseColor("#10B981"));
                badgeGovernor.setBackgroundColor(Color.parseColor("#1F293D"));
            }
            if (lblGovDetail != null) {
                lblGovDetail.setText(String.format(Locale.US, "Active: %.1fA (100%%) | Peak %.0f°C (Optimal <65°C)",
                        gov.baselineMaxc, gov.peakTemp));
                lblGovDetail.setTextColor(Color.parseColor("#9CA3AF"));
            }
            if (bannerThermalDerate != null) bannerThermalDerate.setVisibility(View.GONE);
            bannerDismissed = false;
        }
    }

    private void updateCccvUI(com.mikeland.thunderstruck.evcc.monitor.model.CccvGovernorTelemetry cccv) {
        if (cccv == null) return;

        if (cccvChartView != null) {
            cccvChartView.setTelemetry(cccv);
        }

        if (cccvValVpack != null) {
            cccvValVpack.setText(String.format(Locale.US, "%.1f V", cccv.packVoltage));
        }
        if (cccvValVcell != null) {
            cccvValVcell.setText(String.format(Locale.US, "%.3f V/c", cccv.cellVoltage));
        }
        if (cccvValItarget != null) {
            cccvValItarget.setText(String.format(Locale.US, "%.1f A", cccv.targetAmps));
        }
        if (cccvValIactive != null) {
            cccvValIactive.setText(String.format(Locale.US, "%.1f A", cccv.activeCurrent));
        }

        if (cccvPhaseBadge != null) {
            String writeStr = cccv.writesThisSession > 0 ? (" [" + cccv.writesThisSession + " writes]") : "";
            cccvPhaseBadge.setText(cccv.phase.replace('_', ' ') + writeStr);
            if ("COMPLETE".equals(cccv.phase)) {
                cccvPhaseBadge.setTextColor(Color.parseColor("#10B981"));
            } else if (cccv.isTapering) {
                cccvPhaseBadge.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                cccvPhaseBadge.setTextColor(Color.parseColor("#38BDF8"));
            }
        }

        if (badgeCccv != null) {
            String writeStr = cccv.writesThisSession > 0 ? (" (" + cccv.writesThisSession + "w)") : "";
            if (!cccv.enabled) {
                badgeCccv.setText("⚪ CC/CV: OFF");
                badgeCccv.setTextColor(Color.parseColor("#9CA3AF"));
            } else if ("COMPLETE".equals(cccv.phase)) {
                badgeCccv.setText("✅ CC/CV: COMPLETE" + writeStr);
                badgeCccv.setTextColor(Color.parseColor("#10B981"));
            } else if (cccv.isTapering) {
                badgeCccv.setText(String.format(Locale.US, "📉 %s (%.1fA)%s", cccv.phase.replace('_', ' '), cccv.targetAmps, writeStr));
                badgeCccv.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                badgeCccv.setText(String.format(Locale.US, "⚡ BULK CC (%.1fA)%s", cccv.targetAmps, writeStr));
                badgeCccv.setTextColor(Color.parseColor("#C4B5FD"));
            }
        }
    }

    private void showCccvSettingsDialog() {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle("⚙️ CC/CV Charge Curve Configuration");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 20, 32, 10);
        layout.setBackgroundColor(Color.parseColor("#111827"));

        TextView tvDesc = new TextView(context);
        tvDesc.setText("Configure 5-point voltage and current derating profile to protect 36S Tesla pack from IR sag and cell overvoltage.");
        tvDesc.setTextColor(Color.parseColor("#9CA3AF"));
        tvDesc.setTextSize(12f);
        tvDesc.setPadding(0, 0, 0, 16);
        layout.addView(tvDesc);

        // Preset buttons row
        android.widget.LinearLayout rowPresets = new android.widget.LinearLayout(context);
        rowPresets.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowPresets.setPadding(0, 0, 0, 16);

        Button btnP1 = new Button(context);
        btnP1.setText("Conservative (4.10V)");
        btnP1.setTextSize(11f);
        btnP1.setTextColor(Color.WHITE);
        btnP1.setBackgroundColor(Color.parseColor("#374151"));
        btnP1.setOnClickListener(v -> {
            client.sendCccvPreset("conservative");
            Toast.makeText(context, "Applied 36S Conservative (4.10V)", Toast.LENGTH_SHORT).show();
        });

        Button btnP2 = new Button(context);
        btnP2.setText("Standard (4.15V)");
        btnP2.setTextSize(11f);
        btnP2.setTextColor(Color.WHITE);
        btnP2.setBackgroundColor(Color.parseColor("#374151"));
        btnP2.setOnClickListener(v -> {
            client.sendCccvPreset("standard");
            Toast.makeText(context, "Applied 36S Standard (4.15V)", Toast.LENGTH_SHORT).show();
        });

        Button btnP3 = new Button(context);
        btnP3.setText("Max (4.20V)");
        btnP3.setTextSize(11f);
        btnP3.setTextColor(Color.WHITE);
        btnP3.setBackgroundColor(Color.parseColor("#374151"));
        btnP3.setOnClickListener(v -> {
            client.sendCccvPreset("max_range");
            Toast.makeText(context, "Applied 36S Max Range (4.20V)", Toast.LENGTH_SHORT).show();
        });

        android.widget.LinearLayout.LayoutParams btnParams = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(4, 0, 4, 0);
        rowPresets.addView(btnP1, btnParams);
        rowPresets.addView(btnP2, btnParams);
        rowPresets.addView(btnP3, btnParams);
        layout.addView(rowPresets);

        // Enable / Disable toggle
        Switch swEnable = new Switch(context);
        boolean isEnabled = (lastTelemetry != null && lastTelemetry.cccv != null) ? lastTelemetry.cccv.enabled : true;
        swEnable.setText("Enable CC/CV Dynamic Tapering");
        swEnable.setTextColor(Color.WHITE);
        swEnable.setChecked(isEnabled);
        swEnable.setOnCheckedChangeListener((bv, isChecked) -> client.sendCccvToggle(isChecked));
        layout.addView(swEnable);

        // Fast Cutoff toggle (Immediate 0A cutoff at ceiling to protect EEPROM)
        Switch swFastCutoff = new Switch(context);
        boolean isFastCutoff = (lastTelemetry != null && lastTelemetry.cccv != null && lastTelemetry.cccv.profile != null)
                ? lastTelemetry.cccv.profile.fastCutoff : true;
        swFastCutoff.setText("Clean Fast Cutoff at Ceiling (Save EEPROM)");
        swFastCutoff.setTextColor(Color.parseColor("#34D399"));
        swFastCutoff.setChecked(isFastCutoff);
        swFastCutoff.setPadding(0, 10, 0, 10);
        swFastCutoff.setOnCheckedChangeListener((bv, isChecked) -> {
            if (lastTelemetry != null && lastTelemetry.cccv != null && lastTelemetry.cccv.profile != null) {
                lastTelemetry.cccv.profile.fastCutoff = isChecked;
                client.sendCccvProfile(lastTelemetry.cccv.profile);
            }
        });
        layout.addView(swFastCutoff);

        // Ramp Mode toggle (Linear vs Step)
        Switch swSmoothLinear = new Switch(context);
        boolean isSmooth = (lastTelemetry != null && lastTelemetry.cccv != null && lastTelemetry.cccv.profile != null)
                ? lastTelemetry.cccv.profile.smoothLinear : false;
        swSmoothLinear.setText("Smooth Linear Ramp (Off = Discrete Steps)");
        swSmoothLinear.setTextColor(Color.WHITE);
        swSmoothLinear.setChecked(isSmooth);
        swSmoothLinear.setPadding(0, 10, 0, 10);
        swSmoothLinear.setOnCheckedChangeListener((bv, isChecked) -> {
            if (lastTelemetry != null && lastTelemetry.cccv != null && lastTelemetry.cccv.profile != null) {
                lastTelemetry.cccv.profile.smoothLinear = isChecked;
                client.sendCccvProfile(lastTelemetry.cccv.profile);
            }
        });
        layout.addView(swSmoothLinear);

        ScrollView sc = new ScrollView(context);
        sc.addView(layout);
        builder.setView(sc);
        builder.setPositiveButton("Close", (d, w) -> d.dismiss());
        builder.show();
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
        float totalKw = 0f;
        float totalA = 0f;
        boolean anyActive = false;
        boolean anyOvertemp = false;
        for (int i = 0; i < 4; i++) {
            ChargerTelemetry ch = t.getCharger(i);
            if (ch != null) {
                totalKw += (ch.power / 1000.0f);
                totalA += ch.current;
                if (ch.active || ch.current > 0.2f) anyActive = true;
                if (ch.overtemp) anyOvertemp = true;
            }
        }

        if ("CHARGE".equalsIgnoreCase(raw) || anyActive) {
            return String.format(Locale.US, "CHARGING (%.1f kW / %.1f A)", totalKw, totalA);
        }
        if (anyOvertemp) {
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

    private void updateSimButtonUI(boolean simActive) {
        if (btnSimToggle == null) return;
        if (simActive) {
            btnSimToggle.setText("🧪 SIMULATOR: ON");
            btnSimToggle.setBackgroundColor(Color.parseColor("#8B5CF6"));
        } else {
            btnSimToggle.setText("⚡ LIVE DATA");
            btnSimToggle.setBackgroundColor(Color.parseColor("#059669"));
        }
    }

    private void updatePresetButtonsUI(int selectedIdx) {
        for (int i = 0; i < scButtons.length; i++) {
            if (scButtons[i] != null) {
                if (i == selectedIdx) {
                    scButtons[i].setBackgroundColor(Color.parseColor("#8B5CF6"));
                    scButtons[i].setTextColor(Color.WHITE);
                } else {
                    scButtons[i].setBackgroundColor(Color.parseColor("#1F223A"));
                    scButtons[i].setTextColor(Color.parseColor("#E2E8F0"));
                }
            }
        }
    }

    private void updateBadgeIpUI(boolean connected, String status) {
        if (badgeIp == null) return;
        badgeIp.setText(status);
        if (connected) {
            badgeIp.setTextColor(Color.parseColor("#10B981"));
            badgeIp.setBackgroundColor(Color.parseColor("#1F293D"));
        } else {
            badgeIp.setTextColor(Color.parseColor("#EF4444"));
            badgeIp.setBackgroundColor(Color.parseColor("#1F293D"));
        }
    }
}
