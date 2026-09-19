package com.thunderstruck.evcc.monitor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.thunderstruck.evcc.monitor.model.SessionDataPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChargingChartView extends View {

    private final List<SessionDataPoint> dataPoints = new ArrayList<>();
    private final int MAX_POINTS = 150;

    // Voltage histogram range: 128V min to 150V max
    private final float MIN_VOLTAGE = 128.0f;
    private final float MAX_VOLTAGE = 150.0f;

    private Paint gridPaint;
    private Paint textPaint;
    private Paint voltageLinePaint;
    private Paint currentLinePaint;
    private Paint bgPaint;

    private int voltageColor = Color.parseColor("#06B6D4");  // Cyan for Voltage
    private int currentColor = Color.parseColor("#10B981");  // Green for Current
    private int gridColor = Color.parseColor("#1E293B");     // Dark slate grid
    private int textColor = Color.parseColor("#94A3B8");     // Muted gray
    private int bgColor = Color.parseColor("#000000");       // Pure black matching dashboard

    public ChargingChartView(Context context) {
        super(context);
        init();
    }

    public ChargingChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChargingChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
        textPaint.setTextSize(22f);
        textPaint.setTypeface(Typeface.MONOSPACE);

        voltageLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        voltageLinePaint.setColor(voltageColor);
        voltageLinePaint.setStrokeWidth(3.5f);
        voltageLinePaint.setStyle(Paint.Style.STROKE);
        voltageLinePaint.setStrokeCap(Paint.Cap.ROUND);
        voltageLinePaint.setStrokeJoin(Paint.Join.ROUND);

        currentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentLinePaint.setColor(currentColor);
        currentLinePaint.setStrokeWidth(3.5f);
        currentLinePaint.setStyle(Paint.Style.STROKE);
        currentLinePaint.setStrokeCap(Paint.Cap.ROUND);
        currentLinePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColors(int vColor, int aColor) {
        this.voltageColor = vColor;
        this.currentColor = aColor;
        voltageLinePaint.setColor(vColor);
        currentLinePaint.setColor(aColor);
        invalidate();
    }

    public void applyTheme(@Nullable Typeface font, int titleColor, int valueColor, int indicatorColor, int negativeColor, int tickColor) {
        if (font != null) {
            textPaint.setTypeface(font);
        }
        if (tickColor != 0) {
            gridColor = tickColor;
            gridPaint.setColor(gridColor);
        }
        if (titleColor != 0) {
            textColor = titleColor;
        }
        if (indicatorColor != 0 && indicatorColor != Color.WHITE) {
            voltageColor = indicatorColor;
        } else {
            voltageColor = Color.parseColor("#06B6D4");
        }
        voltageLinePaint.setColor(voltageColor);

        // Current scale & line is explicitly green as requested
        currentColor = Color.parseColor("#10B981");
        currentLinePaint.setColor(currentColor);

        invalidate();
    }

    public synchronized void addDataPoint(float voltage, float current) {
        dataPoints.add(new SessionDataPoint(System.currentTimeMillis(), voltage, current));
        if (dataPoints.size() > MAX_POINTS) {
            dataPoints.remove(0);
        }
        if (isShown()) {
            postInvalidate();
        }
    }

    public synchronized void clearData() {
        dataPoints.clear();
        postInvalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (!isShown()) return;
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw background
        canvas.drawRect(0, 0, w, h, bgPaint);

        float padL = 75f;
        float padR = 75f;
        float padT = 32f;
        float padB = 28f;
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        if (plotW <= 0 || plotH <= 0) return;

        if (dataPoints.size() < 2) {
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Awaiting live charge session telemetry...", w / 2f, h / 2f + 8f, textPaint);
            return;
        }

        // Fixed voltage range: 128V to 150V
        float minV = MIN_VOLTAGE;
        float maxV = MAX_VOLTAGE;

        // Current range: 0 to dynamic max
        float minA = 0.0f;
        float maxA = 5.0f;
        for (SessionDataPoint p : dataPoints) {
            if (p.current > maxA) maxA = p.current;
        }
        maxA = (float) Math.ceil(maxA * 1.2f);

        // Draw 5 horizontal grid lines & Axis Labels (from top = 150V down to bottom = 128V)
        for (int i = 0; i <= 4; i++) {
            float frac = 1.0f - (i / 4.0f);
            float y = padT + (plotH / 4.0f) * i;

            // Grid line
            canvas.drawLine(padL, y, w - padR, y, gridPaint);

            // Voltage label (Left Y-Axis): 150.0V, 144.5V, 139.0V, 133.5V, 128.0V
            float vVal = minV + (maxV - minV) * frac;
            textPaint.setColor(voltageColor);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.US, "%.1fV", vVal), padL - 8f, y + 7f, textPaint);

            // Current label (Right Y-Axis): maxA down to 0.0A
            float aVal = minA + (maxA - minA) * frac;
            textPaint.setColor(currentColor);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format(Locale.US, "%.1fA", aVal), w - padR + 8f, y + 7f, textPaint);
        }

        // Draw Voltage and Current Paths
        Path vPath = new Path();
        Path aPath = new Path();

        int n = dataPoints.size();
        for (int i = 0; i < n; i++) {
            SessionDataPoint p = dataPoints.get(i);
            float x = padL + (i / (float)(n - 1)) * plotW;

            // Clamp points within [minV, maxV] and [minA, maxA] to keep curve neatly inside plot bounds
            float vClamped = Math.max(minV, Math.min(maxV, p.voltage));
            float aClamped = Math.max(minA, Math.min(maxA, p.current));

            float yV = padT + plotH - ((vClamped - minV) / (maxV - minV)) * plotH;
            float yA = padT + plotH - ((aClamped - minA) / (maxA - minA)) * plotH;

            if (i == 0) {
                vPath.moveTo(x, yV);
                aPath.moveTo(x, yA);
            } else {
                vPath.lineTo(x, yV);
                aPath.lineTo(x, yA);
            }
        }

        canvas.drawPath(vPath, voltageLinePaint);
        canvas.drawPath(aPath, currentLinePaint);

        // Legend at top
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(voltageColor);
        canvas.drawText("\u2014 Voltage (128-150V)", padL + 10f, padT - 10f, textPaint);

        textPaint.setColor(currentColor);
        canvas.drawText("\u2014 Current (A)", padL + 250f, padT - 10f, textPaint);
    }
}
