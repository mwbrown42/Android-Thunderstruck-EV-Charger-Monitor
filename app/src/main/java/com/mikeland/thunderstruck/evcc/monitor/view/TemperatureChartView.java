package com.mikeland.thunderstruck.evcc.monitor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TemperatureChartView extends View {

    private static class TempDataPoint {
        final float[] temps = new float[4];

        TempDataPoint(float c1, float c2, float c3, float c4) {
            this.temps[0] = c1;
            this.temps[1] = c2;
            this.temps[2] = c3;
            this.temps[3] = c4;
        }
    }

    private final List<TempDataPoint> dataPoints = new ArrayList<>();
    private final int MAX_POINTS = 150;

    // Temperature axis range: 20°C min to 90°C max (accommodates 85°C TSM2500 trip ceiling)
    private final float MIN_TEMP = 20.0f;
    private final float MAX_TEMP = 90.0f;

    private Paint gridPaint;
    private Paint textPaint;
    private Paint warningLinePaint;
    private Paint criticalLinePaint;
    private final Paint[] chargerLinePaints = new Paint[4];
    private Paint bgPaint;

    private final int[] chargerColors = new int[]{
            Color.parseColor("#FB923C"), // C1: Warm Orange
            Color.parseColor("#E879F9"), // C2: Orchid / Violet
            Color.parseColor("#38BDF8"), // C3: Sky Blue
            Color.parseColor("#FACC15")  // C4: Bright Yellow
    };

    private int warningColor = Color.parseColor("#F59E0B");  // Amber for 66°C derate line
    private int criticalColor = Color.parseColor("#EF4444"); // Red for 85°C trip line
    private int gridColor = Color.parseColor("#1E293B");     // Dark slate
    private int textColor = Color.parseColor("#94A3B8");     // Muted gray
    private int bgColor = Color.parseColor("#000000");

    public TemperatureChartView(Context context) {
        super(context);
        init();
    }

    public TemperatureChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TemperatureChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint();
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(20f);
        textPaint.setColor(textColor);
        textPaint.setTypeface(Typeface.MONOSPACE);

        warningLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warningLinePaint.setColor(warningColor);
        warningLinePaint.setStrokeWidth(2f);
        warningLinePaint.setStyle(Paint.Style.STROKE);
        warningLinePaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        criticalLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        criticalLinePaint.setColor(criticalColor);
        criticalLinePaint.setStrokeWidth(2f);
        criticalLinePaint.setStyle(Paint.Style.STROKE);
        criticalLinePaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        for (int i = 0; i < 4; i++) {
            chargerLinePaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            chargerLinePaints[i].setColor(chargerColors[i]);
            chargerLinePaints[i].setStrokeWidth(3.5f);
            chargerLinePaints[i].setStyle(Paint.Style.STROKE);
            chargerLinePaints[i].setStrokeCap(Paint.Cap.ROUND);
            chargerLinePaints[i].setStrokeJoin(Paint.Join.ROUND);
        }
    }

    public void applyTheme(@Nullable Typeface font, int titleColor, int valueColor, int indicatorColor, int negativeColor, int tickColor) {
        if (font != null) {
            textPaint.setTypeface(font);
        }
        if (tickColor != 0) {
            gridColor = tickColor;
            gridPaint.setColor(gridColor);
        }
        invalidate();
    }

    public synchronized void addDataPoint(float c1Temp, float c2Temp) {
        addDataPoint(c1Temp, c2Temp, 0f, 0f);
    }

    public synchronized void addDataPoint(float c1Temp, float c2Temp, float c3Temp, float c4Temp) {
        if (dataPoints.size() >= MAX_POINTS) {
            dataPoints.remove(0);
        }
        dataPoints.add(new TempDataPoint(c1Temp, c2Temp, c3Temp, c4Temp));
        postInvalidate();
    }

    public synchronized void clearData() {
        dataPoints.clear();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);

        float paddingLeft = 65f;
        float paddingRight = 20f;
        float paddingTop = 32f;
        float paddingBottom = 28f;

        float chartW = w - paddingLeft - paddingRight;
        float chartH = h - paddingTop - paddingBottom;
        if (chartW <= 0 || chartH <= 0) return;

        // Border
        canvas.drawRect(paddingLeft, paddingTop, paddingLeft + chartW, paddingTop + chartH, gridPaint);

        // Horizontal Grid Lines (every 10°C: 20, 30, 40, 50, 60, 70, 80, 90)
        for (int t = 20; t <= 90; t += 10) {
            float norm = (t - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
            float y = paddingTop + chartH - (norm * chartH);

            canvas.drawLine(paddingLeft, y, paddingLeft + chartW, y, gridPaint);

            String label = t + "\u00B0C";
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(label, paddingLeft - 8f, y + 6f, textPaint);
        }

        // Derate threshold line: 66°C (Active thermal governor derate)
        float norm66 = (66.0f - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
        float y66 = paddingTop + chartH - (norm66 * chartH);
        canvas.drawLine(paddingLeft, y66, paddingLeft + chartW, y66, warningLinePaint);
        textPaint.setColor(warningColor);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("66\u00B0 DERATE", paddingLeft + chartW - 6f, y66 - 4f, textPaint);

        // Emergency trip line: 85°C (TSM2500 internal hardware shutdown threshold)
        float norm85 = (85.0f - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
        float y85 = paddingTop + chartH - (norm85 * chartH);
        canvas.drawLine(paddingLeft, y85, paddingLeft + chartW, y85, criticalLinePaint);
        textPaint.setColor(criticalColor);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("85\u00B0 TRIP", paddingLeft + chartW - 6f, y85 - 4f, textPaint);

        // Header Title / Legend
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawText("\uD83C\uDF21\uFE0F Temperature History", paddingLeft, paddingTop - 12f, textPaint);

        // Draw Live Values in Header for all active chargers
        float[] lastTemps = new float[4];
        synchronized (this) {
            if (!dataPoints.isEmpty()) {
                TempDataPoint last = dataPoints.get(dataPoints.size() - 1);
                System.arraycopy(last.temps, 0, lastTemps, 0, 4);
            }
        }

        float legendX = paddingLeft + 250f;
        for (int ch = 0; ch < 4; ch++) {
            if (ch == 0 || lastTemps[ch] > 0.0f) {
                textPaint.setColor(chargerColors[ch]);
                String label = String.format(Locale.US, "C%d: %.1f\u00B0C", ch + 1, lastTemps[ch]);
                canvas.drawText(label, legendX, paddingTop - 12f, textPaint);
                legendX += 115f;
            }
        }

        // Draw temperature line traces
        synchronized (this) {
            int n = dataPoints.size();
            if (n < 2) {
                if (n == 0) {
                    textPaint.setColor(textColor);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("Awaiting session temperature data...", paddingLeft + chartW / 2f, paddingTop + chartH / 2f, textPaint);
                }
                return;
            }

            Path[] paths = new Path[4];
            boolean[] started = new boolean[4];
            for (int ch = 0; ch < 4; ch++) {
                paths[ch] = new Path();
            }

            for (int i = 0; i < n; i++) {
                TempDataPoint pt = dataPoints.get(i);
                float x = paddingLeft + ((float) i / (float) (n - 1)) * chartW;

                for (int ch = 0; ch < 4; ch++) {
                    float temp = pt.temps[ch];
                    if (ch == 0 || temp > 0.0f) {
                        float norm = (Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp)) - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
                        float y = paddingTop + chartH - (norm * chartH);
                        if (!started[ch]) {
                            paths[ch].moveTo(x, y);
                            started[ch] = true;
                        } else {
                            paths[ch].lineTo(x, y);
                        }
                    }
                }
            }

            for (int ch = 0; ch < 4; ch++) {
                if (started[ch]) {
                    canvas.drawPath(paths[ch], chargerLinePaints[ch]);
                }
            }
        }
    }
}
