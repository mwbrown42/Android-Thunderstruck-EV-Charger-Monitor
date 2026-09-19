package com.thunderstruck.evcc.monitor.view;

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
        float c1Temp;
        float c2Temp;

        TempDataPoint(float c1, float c2) {
            this.c1Temp = c1;
            this.c2Temp = c2;
        }
    }

    private final List<TempDataPoint> dataPoints = new ArrayList<>();
    private final int MAX_POINTS = 150;

    // Temperature axis range: 20°C min to 70°C max
    private final float MIN_TEMP = 20.0f;
    private final float MAX_TEMP = 70.0f;

    private Paint gridPaint;
    private Paint textPaint;
    private Paint warningLinePaint;
    private Paint criticalLinePaint;
    private Paint c1LinePaint;
    private Paint c2LinePaint;
    private Paint bgPaint;

    private int c1Color = Color.parseColor("#FB923C");       // Warm Orange for Charger 1
    private int c2Color = Color.parseColor("#E879F9");       // Orchid / Violet for Charger 2
    private int warningColor = Color.parseColor("#F59E0B");  // Amber for 53°C derate line
    private int criticalColor = Color.parseColor("#EF4444"); // Red for 60°C trip line
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

        c1LinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        c1LinePaint.setColor(c1Color);
        c1LinePaint.setStrokeWidth(3.5f);
        c1LinePaint.setStyle(Paint.Style.STROKE);
        c1LinePaint.setStrokeCap(Paint.Cap.ROUND);
        c1LinePaint.setStrokeJoin(Paint.Join.ROUND);

        c2LinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        c2LinePaint.setColor(c2Color);
        c2LinePaint.setStrokeWidth(3.5f);
        c2LinePaint.setStyle(Paint.Style.STROKE);
        c2LinePaint.setStrokeCap(Paint.Cap.ROUND);
        c2LinePaint.setStrokeJoin(Paint.Join.ROUND);
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
        if (dataPoints.size() >= MAX_POINTS) {
            dataPoints.remove(0);
        }
        dataPoints.add(new TempDataPoint(c1Temp, c2Temp));
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

        // Horizontal Grid Lines (every 10°C: 20, 30, 40, 50, 60, 70)
        for (int t = 20; t <= 70; t += 10) {
            float norm = (t - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
            float y = paddingTop + chartH - (norm * chartH);

            canvas.drawLine(paddingLeft, y, paddingLeft + chartW, y, gridPaint);

            String label = t + "°C";
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(label, paddingLeft - 8f, y + 6f, textPaint);
        }

        // Derate threshold line: 53°C
        float norm53 = (53.0f - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
        float y53 = paddingTop + chartH - (norm53 * chartH);
        canvas.drawLine(paddingLeft, y53, paddingLeft + chartW, y53, warningLinePaint);
        textPaint.setColor(warningColor);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("53\u00B0 DERATE", paddingLeft + chartW - 6f, y53 - 4f, textPaint);

        // Emergency trip line: 64°C
        float norm64 = (64.0f - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
        float y64 = paddingTop + chartH - (norm64 * chartH);
        canvas.drawLine(paddingLeft, y64, paddingLeft + chartW, y64, criticalLinePaint);
        textPaint.setColor(criticalColor);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("64\u00B0 TRIP", paddingLeft + chartW - 6f, y64 - 4f, textPaint);

        // Header Title / Legend
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawText("🌡️ Temperature History", paddingLeft, paddingTop - 12f, textPaint);

        // Draw Live Values in Header if available
        float curC1 = 0.0f;
        float curC2 = 0.0f;
        synchronized (this) {
            if (!dataPoints.isEmpty()) {
                TempDataPoint last = dataPoints.get(dataPoints.size() - 1);
                curC1 = last.c1Temp;
                curC2 = last.c2Temp;
            }
        }

        float legendX = paddingLeft + 250f;
        textPaint.setColor(c1Color);
        canvas.drawText(String.format(Locale.US, "C1: %.1f°C", curC1), legendX, paddingTop - 12f, textPaint);

        if (curC2 > 0.0f) {
            textPaint.setColor(c2Color);
            canvas.drawText(String.format(Locale.US, "C2: %.1f°C", curC2), legendX + 130f, paddingTop - 12f, textPaint);
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

            Path c1Path = new Path();
            Path c2Path = new Path();
            boolean c1Started = false;
            boolean c2Started = false;

            for (int i = 0; i < n; i++) {
                TempDataPoint pt = dataPoints.get(i);
                float x = paddingLeft + ((float) i / (float) (n - 1)) * chartW;

                // C1
                float normC1 = (Math.max(MIN_TEMP, Math.min(MAX_TEMP, pt.c1Temp)) - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
                float y1 = paddingTop + chartH - (normC1 * chartH);
                if (!c1Started) {
                    c1Path.moveTo(x, y1);
                    c1Started = true;
                } else {
                    c1Path.lineTo(x, y1);
                }

                // C2
                if (pt.c2Temp > 0.0f) {
                    float normC2 = (Math.max(MIN_TEMP, Math.min(MAX_TEMP, pt.c2Temp)) - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
                    float y2 = paddingTop + chartH - (normC2 * chartH);
                    if (!c2Started) {
                        c2Path.moveTo(x, y2);
                        c2Started = true;
                    } else {
                        c2Path.lineTo(x, y2);
                    }
                }
            }

            canvas.drawPath(c1Path, c1LinePaint);
            if (c2Started) {
                canvas.drawPath(c2Path, c2LinePaint);
            }
        }
    }
}

