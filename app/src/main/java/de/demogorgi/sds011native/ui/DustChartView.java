package de.demogorgi.sds011native.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.demogorgi.sds011native.model.HistoryPoint;

public final class DustChartView extends View {
    private static final int PM25 = Color.rgb(25, 137, 166);
    private static final int PM10 = Color.rgb(126, 90, 213);
    private static final int GRID = Color.rgb(220, 220, 216);
    private static final int TEXT = Color.rgb(88, 88, 82);
    private static final int SAVED = Color.argb(55, 120, 120, 112);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot = new RectF();
    private List<HistoryPoint> points = new ArrayList<>();
    private boolean raw;

    public DustChartView(Context context) {
        super(context);
    }

    public DustChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(List<HistoryPoint> points, boolean raw) {
        this.points = points == null ? new ArrayList<HistoryPoint>() : points;
        this.raw = raw;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);

        plot.set(dp(28), dp(12), getWidth() - dp(10), getHeight() - dp(26));
        if (plot.width() <= 0 || plot.height() <= 0) {
            return;
        }
        drawGrid(canvas);
        if (points.isEmpty()) {
            drawEmpty(canvas);
            return;
        }
        drawSavedBands(canvas);
        drawSeries(canvas, true);
        drawSeries(canvas, false);
        drawLegend(canvas);
    }

    private void drawGrid(Canvas canvas) {
        paint.setStrokeWidth(1f);
        paint.setColor(GRID);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i <= 4; i++) {
            float y = plot.top + plot.height() * i / 4f;
            canvas.drawLine(plot.left, y, plot.right, y, paint);
        }
        paint.setColor(TEXT);
        paint.setTextSize(sp(11));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText("ug/m3", plot.left, plot.top - dp(3), paint);
    }

    private void drawEmpty(Canvas canvas) {
        paint.setColor(TEXT);
        paint.setTextSize(sp(14));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(raw ? "Warte auf Rohwerte" : "Noch keine Mittelwerte",
                getWidth() / 2f, plot.centerY(), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSavedBands(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SAVED);
        for (HistoryPoint point : points) {
            if (!point.recorded) {
                continue;
            }
            float x = xFor(point.epochMillis);
            canvas.drawRect(x - dp(2), plot.top, x + dp(2), plot.bottom, paint);
        }
    }

    private void drawSeries(Canvas canvas, boolean pm25) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pm25 ? PM25 : PM10);
        float radius = raw ? dp(3) : dp(4);
        for (HistoryPoint point : points) {
            float x = xFor(point.epochMillis);
            float y = yFor(pm25 ? point.pm25 : point.pm10);
            if (!point.usable && raw) {
                paint.setAlpha(95);
            } else {
                paint.setAlpha(255);
            }
            canvas.drawCircle(x, y, radius, paint);
        }
        paint.setAlpha(255);
    }

    private void drawLegend(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(sp(11));
        paint.setColor(PM25);
        canvas.drawCircle(plot.left, plot.bottom + dp(17), dp(4), paint);
        paint.setColor(TEXT);
        canvas.drawText("PM2.5", plot.left + dp(9), plot.bottom + dp(21), paint);
        paint.setColor(PM10);
        canvas.drawCircle(plot.left + dp(72), plot.bottom + dp(17), dp(4), paint);
        paint.setColor(TEXT);
        canvas.drawText("PM10", plot.left + dp(81), plot.bottom + dp(21), paint);
    }

    private float xFor(long epochMillis) {
        if (points.size() <= 1) {
            return plot.right;
        }
        long min = points.get(0).epochMillis;
        long max = points.get(points.size() - 1).epochMillis;
        if (max <= min) {
            return plot.right;
        }
        return plot.left + plot.width() * (epochMillis - min) / (float) (max - min);
    }

    private float yFor(double value) {
        double max = 10.0;
        for (HistoryPoint point : points) {
            max = Math.max(max, Math.max(point.pm25, point.pm10));
        }
        max = Math.ceil(max / 10.0) * 10.0;
        return (float) (plot.bottom - plot.height() * Math.min(max, value) / max);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
