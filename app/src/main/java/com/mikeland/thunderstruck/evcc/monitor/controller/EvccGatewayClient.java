package com.mikeland.thunderstruck.evcc.monitor.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mikeland.thunderstruck.evcc.monitor.model.EvccTelemetry;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class EvccGatewayClient {
    private static final String TAG = "EvccGatewayClient";
    private static EvccGatewayClient instance;

    public interface EvccEventListener {
        void onConnectionStateChanged(boolean connected, String status);
        void onTelemetryReceived(EvccTelemetry telemetry);
        void onRawLineReceived(String line, boolean isTx);
        void onQueryResponseReceived(String query, String text);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private EvccEventListener listener;

    private boolean isSimulationMode = false;
    private boolean isConnected = false;
    private volatile boolean isWsConnected = false;
    private volatile long lastUdpRxTime = 0;
    private boolean lastReportedConnecting = false;
    private boolean lastReportedFailure = false;
    private String gatewayIp = "";
    private String gatewayPort = "80";

    private Thread udpReceiverThread;
    private volatile boolean udpRunning = false;
    private DatagramSocket udpSocket;

    private final EvccTelemetry currentTelemetry = new EvccTelemetry();
    private final EvccSimulatorEngine simulator = EvccSimulatorEngine.getInstance();

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSimulationMode) {
                boolean udpActive = (System.currentTimeMillis() - lastUdpRxTime < 4000);
                boolean active = udpActive || isWsConnected;
                if (isConnected != active) {
                    isConnected = active;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onConnectionStateChanged(isConnected, isConnected ? "Online" : "Offline"));
                    }
                }
            }
            mainHandler.postDelayed(this, 2000);
        }
    };

    public static synchronized EvccGatewayClient getInstance() {
        if (instance == null) {
            instance = new EvccGatewayClient();
        }
        return instance;
    }

    public void addListener(EvccEventListener listener) {
        this.listener = listener;
    }

    public void removeListener(EvccEventListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    private EvccGatewayClient() {
        httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .build();

        simulator.setListener(new EvccSimulatorEngine.SimulatorListener() {
            @Override
            public void onTelemetryUpdate(EvccTelemetry telemetry) {
                if (isSimulationMode && listener != null) {
                    mainHandler.post(() -> listener.onTelemetryReceived(telemetry));
                }
            }

            @Override
            public void onRawLineEmitted(String line, boolean isTx) {
                if (isSimulationMode && listener != null) {
                    mainHandler.post(() -> listener.onRawLineReceived(line, isTx));
                }
            }

            @Override
            public void onQueryResponse(String queryType, String text) {
                if (isSimulationMode && listener != null) {
                    mainHandler.post(() -> listener.onQueryResponseReceived(queryType, text));
                }
            }
        });

        startUdpListener();
        mainHandler.postDelayed(watchdogRunnable, 2000);
    }

    public synchronized void startUdpListener() {
        if (udpRunning) return;
        udpRunning = true;
        udpReceiverThread = new Thread(() -> {
            byte[] buf = new byte[8192];
            try {
                udpSocket = new DatagramSocket(8888);
                udpSocket.setBroadcast(true);
                udpSocket.setReuseAddress(true);
                Log.i(TAG, "Subnet UDP broadcast listener active on port 8888");
                while (udpRunning && !udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    if (isSimulationMode) continue;

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    String senderIp = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "";

                    // Auto-learn gateway IP from broadcast packet
                    boolean ipChanged = false;
                    if (senderIp != null && !senderIp.isEmpty() && !senderIp.equals(gatewayIp)) {
                        Log.i(TAG, "Auto-discovered EVCC Gateway IP via UDP: " + senderIp);
                        gatewayIp = senderIp;
                        ipChanged = true;
                    }

                    try {
                        JSONObject json = new JSONObject(message);
                        String type = json.optString("type", "");
                        if ("telemetry".equalsIgnoreCase(type)) {
                            currentTelemetry.updateFromJson(json);
                            lastUdpRxTime = System.currentTimeMillis();
                            boolean wasConnected = isConnected;
                            isConnected = true;
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    if (!wasConnected) {
                                        listener.onConnectionStateChanged(true, "Online");
                                    }
                                    listener.onTelemetryReceived(currentTelemetry);
                                }
                            });

                            if ((ipChanged || webSocket == null || !isWsConnected) && !gatewayIp.isEmpty()) {
                                connectWebSocket();
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed parsing UDP broadcast JSON: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (udpRunning) {
                    Log.e(TAG, "UDP listener encountered error: " + e.getMessage());
                }
            } finally {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close();
                }
            }
        }, "EvccUdpBroadcastListener");
        udpReceiverThread.setDaemon(true);
        udpReceiverThread.start();
    }

    public void setListener(EvccEventListener listener) {
        this.listener = listener;
    }

    public String getGatewayIp() {
        return gatewayIp;
    }

    public String getGatewayPort() {
        return gatewayPort;
    }

    public synchronized void setGatewayConfig(String ip, String port) {
        String cleanIp = (ip != null) ? ip.trim() : "";
        String cleanPort = (port != null) ? port.trim() : "";
        if (cleanPort.isEmpty()) cleanPort = "80";

        boolean changed = (!cleanIp.isEmpty() && !cleanIp.equals(this.gatewayIp)) ||
                          (!cleanPort.isEmpty() && !cleanPort.equals(this.gatewayPort));

        if (!cleanIp.isEmpty()) this.gatewayIp = cleanIp;
        if (!cleanPort.isEmpty()) this.gatewayPort = cleanPort;

        if (changed) {
            lastReportedConnecting = false;
            lastReportedFailure = false;
        }

        if (changed && !isSimulationMode) {
            Log.d(TAG, "Gateway config changed to " + gatewayIp + ":" + gatewayPort + ", reconnecting...");
            disconnectWebSocket();
            connectWebSocket();
        }
    }

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSimulationMode && !isConnected) {
                Log.d(TAG, "Attempting auto-reconnect to " + gatewayIp + ":" + gatewayPort);
                connectWebSocket();
            }
        }
    };

    private void scheduleReconnect() {
        if (!isSimulationMode && gatewayIp != null && !gatewayIp.trim().isEmpty()) {
            mainHandler.removeCallbacks(reconnectRunnable);
            mainHandler.postDelayed(reconnectRunnable, 5000);
        }
    }

    public synchronized void setSimulationMode(boolean enable) {
        this.isSimulationMode = enable;
        lastReportedConnecting = false;
        lastReportedFailure = false;
        if (enable) {
            mainHandler.removeCallbacks(reconnectRunnable);
            disconnectWebSocket();
            simulator.start();
            if (listener != null) {
                listener.onConnectionStateChanged(true, "Simulated");
            }
        } else {
            simulator.stop();
            currentTelemetry.reset();
            if (listener != null) {
                mainHandler.post(() -> listener.onTelemetryReceived(currentTelemetry));
            }
            boolean udpActive = (System.currentTimeMillis() - lastUdpRxTime < 4000);
            isConnected = udpActive || isWsConnected;
            if (listener != null) {
                listener.onConnectionStateChanged(isConnected, isConnected ? "Online" : "Offline");
            }
            connectWebSocket();
        }
    }

    public boolean isSimulationMode() {
        return isSimulationMode;
    }

    public boolean isConnected() {
        return isSimulationMode || isConnected;
    }

    public synchronized void connectWebSocket() {
        if (isSimulationMode) return;
        if (gatewayIp == null || gatewayIp.trim().isEmpty()) {
            return;
        }
        mainHandler.removeCallbacks(reconnectRunnable);
        disconnectWebSocket();

        String url = "ws://" + gatewayIp + ":" + gatewayPort + "/ws";
        Request request = new Request.Builder().url(url).build();

        if (!lastReportedConnecting && !isConnected) {
            EvccSessionLogger.getInstance().log("WS", "Attempting connection to " + url);
            lastReportedConnecting = true;
        }

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                isWsConnected = true;
                lastReportedConnecting = false;
                lastReportedFailure = false;
                EvccSessionLogger.getInstance().log("WS", "WebSocket CONNECTED to " + url);
                mainHandler.removeCallbacks(reconnectRunnable);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStateChanged(true, "Online");
                        listener.onRawLineReceived("[Gateway] WebSocket Connected to " + gatewayIp + "\n", false);
                    }
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type", "");
                    if ("telemetry".equals(type)) {
                        currentTelemetry.updateFromJson(json);
                        mainHandler.post(() -> {
                            if (listener != null) listener.onTelemetryReceived(currentTelemetry);
                        });
                    } else if ("raw".equals(type)) {
                        String line = json.optString("line", "");
                        boolean isTx = json.optBoolean("isTx", false);
                        EvccSessionLogger.getInstance().log("RAW", (isTx ? "[TX] " : "[RX] ") + line);
                        mainHandler.post(() -> {
                            if (listener != null) listener.onRawLineReceived(line + "\n", isTx);
                        });
                    } else if ("query_resp".equals(type)) {
                        String query = json.optString("query", "");
                        String respText = json.optString("text", "");
                        EvccSessionLogger.getInstance().log("QUERY_RESP", query + ":\n" + respText);
                        mainHandler.post(() -> {
                            if (listener != null) listener.onQueryResponseReceived(query, respText);
                        });
                    }
                } catch (Exception e) {
                    EvccSessionLogger.getInstance().log("WS_RAW", text);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onRawLineReceived(text + "\n", false);
                    });
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                isWsConnected = false;
                lastReportedConnecting = false;
                EvccSessionLogger.getInstance().log("WS", "WebSocket closing: " + code + " / " + reason);
                mainHandler.post(() -> {
                    boolean udpActive = (System.currentTimeMillis() - lastUdpRxTime < 4000);
                    if (!udpActive) {
                        isConnected = false;
                        if (listener != null) listener.onConnectionStateChanged(false, "Offline");
                    }
                    scheduleReconnect();
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isWsConnected = false;
                String err = (t != null) ? t.getMessage() : "Unknown error";
                if (!lastReportedFailure) {
                    EvccSessionLogger.getInstance().log("WS_ERROR", "WebSocket failure: " + err + " (reconnecting in background...)");
                    lastReportedFailure = true;
                }
                mainHandler.post(() -> {
                    boolean udpActive = (System.currentTimeMillis() - lastUdpRxTime < 4000);
                    if (!udpActive) {
                        isConnected = false;
                        if (listener != null) listener.onConnectionStateChanged(false, "Offline");
                    }
                    scheduleReconnect();
                });
            }
        });
    }

    public synchronized void disconnectWebSocket() {
        mainHandler.removeCallbacks(reconnectRunnable);
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client closed");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        isWsConnected = false;
        boolean udpActive = (System.currentTimeMillis() - lastUdpRxTime < 4000);
        isConnected = udpActive;
    }

    public void sendRawCommand(String cmd) {
        EvccSessionLogger.getInstance().log("CMD_TX", cmd);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet TX] --> " + cmd + "\n", true));
        }
        if (isSimulationMode) {
            simulator.handleCommand(cmd);
        } else if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "raw");
                json.put("command", cmd);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending raw command", e);
                EvccSessionLogger.getInstance().log("CMD_ERR", "Failed sending raw command: " + e.getMessage());
                if (listener != null) {
                    mainHandler.post(() -> listener.onRawLineReceived("[Tablet ERROR] Send failed: " + e.getMessage() + "\n", false));
                }
            }
        } else if (listener != null) {
            EvccSessionLogger.getInstance().log("CMD_WARN", "Cannot send '" + cmd + "': Not connected to ESP32 (" + gatewayIp + ")");
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet WARNING] Cannot send '" + cmd + "': Not connected to ESP32 (" + gatewayIp + ")!\n", false));
        }
    }

    public void sendTraceToggle(String traceType) {
        EvccSessionLogger.getInstance().log("TRACE_TX", traceType);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet Trace] --> trace " + traceType + "\n", true));
        }
        if (isSimulationMode) {
            simulator.handleCommand("trace " + traceType);
        } else if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "trace");
                json.put("trace", traceType);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending trace command", e);
                EvccSessionLogger.getInstance().log("TRACE_ERR", "Failed sending trace: " + e.getMessage());
            }
        } else if (listener != null) {
            EvccSessionLogger.getInstance().log("TRACE_WARN", "Cannot toggle trace: Not connected to ESP32 (" + gatewayIp + ")");
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet WARNING] Cannot toggle trace: Not connected to ESP32 (" + gatewayIp + ")!\n", false));
        }
    }

    public void sendQuery(String queryType) {
        EvccSessionLogger.getInstance().log("QUERY_TX", queryType);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet Query] --> show " + (queryType.equals("show") ? "" : queryType) + "\n", true));
        }
        if (isSimulationMode) {
            simulator.handleCommand("show " + (queryType.equals("show") ? "" : queryType));
        } else if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "query");
                json.put("query", queryType);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending query command", e);
                EvccSessionLogger.getInstance().log("QUERY_ERR", "Failed sending query: " + e.getMessage());
            }
        } else if (listener != null) {
            EvccSessionLogger.getInstance().log("QUERY_WARN", "Cannot send query: Not connected to ESP32 (" + gatewayIp + ")");
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet WARNING] Cannot send query: Not connected to ESP32 (" + gatewayIp + ")!\n", false));
        }
    }

    public void sendParamSet(String param, float value) {
        EvccSessionLogger.getInstance().log("PARAM_TX", param + "=" + value);
        if (isSimulationMode) {
            simulator.handleCommand(String.format(java.util.Locale.US, "set %s %.1f", param, value));
        } else if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "set_param");
                json.put("param", param);
                json.put("value", value);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending param command", e);
                EvccSessionLogger.getInstance().log("PARAM_ERR", "Failed sending param: " + e.getMessage());
            }
        }
    }

    public void sendGovernorToggle(boolean enabled) {
        EvccSessionLogger.getInstance().log("GOV_TX", "enabled=" + enabled);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet Governor] --> " + (enabled ? "ENABLE" : "DISABLE") + "\n", true));
        }
        if (isSimulationMode) {
            simulator.handleGovernorToggle(enabled);
        } else if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "set_governor");
                json.put("enabled", enabled);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending governor toggle", e);
                EvccSessionLogger.getInstance().log("GOV_ERR", "Failed sending governor toggle: " + e.getMessage());
            }
        }
    }

    public void sendCccvToggle(boolean enabled) {
        EvccSessionLogger.getInstance().log("CCCV_TX", "enabled=" + enabled);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet CC/CV] --> " + (enabled ? "ENABLE" : "DISABLE") + "\n", true));
        }
        if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "toggle_cccv");
                json.put("enabled", enabled);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending CC/CV toggle", e);
            }
        }
    }

    public void sendCccvPreset(String preset) {
        EvccSessionLogger.getInstance().log("CCCV_TX", "preset=" + preset);
        if (listener != null) {
            mainHandler.post(() -> listener.onRawLineReceived("[Tablet CC/CV] --> Preset: " + preset + "\n", true));
        }
        if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "set_cccv_preset");
                json.put("preset", preset);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending CC/CV preset", e);
            }
        }
    }

    public void sendCccvProfile(com.mikeland.thunderstruck.evcc.monitor.model.CccvProfile profile) {
        if (profile == null) return;
        EvccSessionLogger.getInstance().log("CCCV_TX", "profile: cells=" + profile.cellCount);
        if (webSocket != null && isConnected) {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "set_cccv_profile");
                json.put("enabled", profile.enabled);
                json.put("cellCount", profile.cellCount);
                json.put("smoothLinear", profile.smoothLinear);
                json.put("fastCutoff", profile.fastCutoff);
                json.put("termAmps", profile.terminationAmps);
                org.json.JSONArray pts = new org.json.JSONArray();
                for (com.mikeland.thunderstruck.evcc.monitor.model.CccvProfile.Point p : profile.points) {
                    JSONObject ptObj = new JSONObject();
                    ptObj.put("v", p.voltage);
                    ptObj.put("a", p.current);
                    pts.put(ptObj);
                }
                json.put("points", pts);
                webSocket.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending CC/CV profile", e);
            }
        }
    }
}
