package com.mikeland.thunderstruck.evcc.monitor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mikeland.thunderstruck.evcc.monitor.model.CccvGovernorTelemetry;
import com.mikeland.thunderstruck.evcc.monitor.model.CccvProfile;

import java.util.Locale;

public class CccvCurveChartView extends View {

    private CccvGovernorTelemetry telemetry = null;

    private Paint bgPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint curveLinePaint;
    private Paint curveFillPaint;
    private Paint pointPaint;
    private Paint pointBorderPaint;
    private Paint crosshairPaint;
    private Paint liveMarkerPaint;
    private Paint liveOuterPaint;

    private int bgColor = Color.parseColor("#0B0F19");
    private int gridColor = Color.parseColor("#1E293B");
    private int textColor = Color.parseColor("#94A3B8");
    private int curveColor = Color.parseColor("#8B5CF6");       // Purple
    private int liveMarkerColor = Color.parseColor("#06B6D4");  // Cyan

    public CccvCurveChartView(Context context) {
        super(context);
        init();
    }

    public CccvCurveChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CccvCurveChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

        curveLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        curveLinePaint.setColor(curveColor);
        curveLinePaint.setStrokeWidth(4.0f);
        curveLinePaint.setStyle(Paint.Style.STROKE);
        curveLinePaint.setStrokeCap(Paint.Cap.ROUND);
        curveLinePaint.setStrokeJoin(Paint.Join.ROUND);

        curveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        curveFillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(curveColor);
        pointPaint.setStyle(Paint.Style.FILL);

        pointBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointBorderPaint.setColor(Color.WHITE);
        pointBorderPaint.setStrokeWidth(2.0f);
        pointBorderPaint.setStyle(Paint.Style.STROKE);

        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setColor(Color.parseColor("#4B5563"));
        crosshairPaint.setStrokeWidth(1.5f);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        liveMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        liveMarkerPaint.setColor(liveMarkerColor);
        liveMarkerPaint.setStyle(Paint.Style.FILL);

        liveOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        liveOuterPaint.setColor(Color.argb(80, 6, 182, 212));
        liveOuterPaint.setStyle(Paint.Style.FILL);
    }

    public void setTelemetry(CccvGovernorTelemetry telemetry) {
        this.telemetry = telemetry;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        float padL = 70f;
        float padR = 40f;
        float padT = 35f;
        float padB = 45f;

        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        if (telemetry == null || telemetry.profile == null || telemetry.profile.points.isEmpty()) {
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Awaiting CC/CV Profile...", w / 2f, h / 2f, textPaint);
            return;
        }

        CccvProfile prof = telemetry.profile;
        int numPts = prof.points.size();

        // X-axis: Pack Voltage bounds
        float minV = (float) Math.floor(prof.points.get(0).voltage - 3.0f);
        float maxV = (float) Math.ceil(prof.points.get(numPts - 1).voltage + 2.0f);
        if (maxV <= minV) maxV = minV + 10.0f;

        // Y-axis: Current bounds (0 to max amps)
        float maxA = 60.0f;
        for (CccvProfile.Point p : prof.points) {
            if (p.current > maxA) maxA = p.current;
        }
        maxA = (float) (Math.ceil(maxA / 10.0f) * 10.0f);
        float minA = 0.0f;

        // Draw horizontal grid lines & Y-labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float frac = 1.0f - (i / 4.0f);
            float y = padT + (plotH / 4.0f) * i;
            canvas.drawLine(padL, y, w - padR, y, gridPaint);

            textPaint.setColor(textColor);
            float aVal = minA + (maxA - minA) * frac;
            canvas.drawText(String.format(Locale.US, "%.0fA", aVal), padL - 8f, y + 8f, textPaint);
        }

        // Draw vertical grid lines & X-labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        int numTicks = 5;
        for (int i = 0; i <= numTicks; i++) {
            float frac = i / (float) numTicks;
            float x = padL + plotW * frac;
            canvas.drawLine(x, padT, x, padT + plotH, gridPaint);

            float vVal = minV + (maxV - minV) * frac;
            float cVal = vVal / (prof.cellCount > 0 ? prof.cellCount : 36);

            textPaint.setColor(textColor);
            canvas.drawText(String.format(Locale.US, "%.1fV", vVal), x, padT + plotH + 22f, textPaint);
            textPaint.setColor(Color.parseColor("#A78BFA"));
            canvas.drawText(String.format(Locale.US, "%.2fV/c", cVal), x, padT + plotH + 40f, textPaint);
        }

        // Helpers to project (v, a) to canvas coordinates
        final float finalMinV = minV;
        final float finalMaxV = maxV;
        final float finalMinA = minA;
        final float finalMaxA = maxA;

        // Create Path for filled area
        Path fillPath = new Path();
        float startX = padL + Math.max(0, Math.min(1, (finalMinV - finalMinV) / (finalMaxV - finalMinV))) * plotW;
        float startY = padT + plotH - Math.max(0, Math.min(1, (prof.points.get(0).current - finalMinA) / (finalMaxA - finalMinA))) * plotH;
        float zeroY = padT + plotH;

        fillPath.moveTo(startX, zeroY);
        fillPath.lineTo(startX, startY);

        Path linePath = new Path();
        linePath.moveTo(startX, startY);

        for (int i = 0; i < numPts; i++) {
            CccvProfile.Point p = prof.points.get(i);
            float px = padL + Math.max(0, Math.min(1, (p.voltage - finalMinV) / (finalMaxV - finalMinV))) * plotW;
            float py = padT + plotH - Math.max(0, Math.min(1, (p.current - finalMinA) / (finalMaxA - finalMinA))) * plotH;

            if (!prof.smoothLinear && i > 0) {
                CccvProfile.Point prev = prof.points.get(i - 1);
                float prevY = padT + plotH - Math.max(0, Math.min(1, (prev.current - finalMinA) / (finalMaxA - finalMinA))) * plotH;
                fillPath.lineTo(px, prevY);
                linePath.lineTo(px, prevY);
            }
            fillPath.lineTo(px, py);
            linePath.lineTo(px, py);
        }

        float endX = padL + Math.max(0, Math.min(1, (finalMaxV - finalMinV) / (finalMaxV - finalMinV))) * plotW;
        fillPath.lineTo(endX, zeroY);
        fillPath.close();

        // Shaded gradient fill
        curveFillPaint.setShader(new LinearGradient(0, padT, 0, padT + plotH,
                Color.argb(80, 139, 92, 246), Color.argb(10, 139, 92, 246), Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, curveFillPaint);

        // Curve stroke
        canvas.drawPath(linePath, curveLinePaint);

        // Draw setpoint circles and labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < numPts; i++) {
            CccvProfile.Point p = prof.points.get(i);
            float px = padL + Math.max(0, Math.min(1, (p.voltage - finalMinV) / (finalMaxV - finalMinV))) * plotW;
            float py = padT + plotH - Math.max(0, Math.min(1, (p.current - finalMinA) / (finalMaxA - finalMinA))) * plotH;

            canvas.drawCircle(px, py, 7f, pointPaint);
            canvas.drawCircle(px, py, 7f, pointBorderPaint);

            textPaint.setColor(Color.parseColor("#DDD6FE"));
            canvas.drawText(String.format(Locale.US, "P%d: %.0fA", i + 1, p.current), px, py - 12f, textPaint);
        }

        // Draw Live Operating Point Marker (if packVoltage > 20V)
        if (telemetry.packVoltage > 20.0f) {
            float liveX = padL + Math.max(0, Math.min(1, (telemetry.packVoltage - finalMinV) / (finalMaxV - finalMinV))) * plotW;
            float liveY = padT + plotH - Math.max(0, Math.min(1, (telemetry.targetAmps - finalMinA) / (finalMaxA - finalMinA))) * plotH;

            // Crosshairs
            canvas.drawLine(liveX, padT, liveX, padT + plotH, crosshairPaint);
            canvas.drawLine(padL, liveY, w - padR, liveY, crosshairPaint);

            // Pulsing live circle
            canvas.drawCircle(liveX, liveY, 14f, liveOuterPaint);
            canvas.drawCircle(liveX, liveY, 8f, liveMarkerPaint);
            canvas.drawCircle(liveX, liveY, 8f, pointBorderPaint);

            // Live Callout Tag
            textPaint.setColor(liveMarkerColor);
            canvas.drawText(String.format(Locale.US, "LIVE: %.1fA", telemetry.targetAmps), liveX, liveY - 18f, textPaint);
        }
    }
}
