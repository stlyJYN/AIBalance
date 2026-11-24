package com.deepseek.balance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A minimal zero-dependency line chart used for balance / consumption trends.
 */
public final class TrendChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String title = "";
    private String unit = "";
    private final List<Float> values = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();

    public TrendChartView(Context context) {
        super(context);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(Color.rgb(38, 38, 42));
        labelPaint.setColor(Color.rgb(161, 161, 170));
        labelPaint.setTextSize(dp(11));
        setBackgroundColor(Color.rgb(15, 15, 17));
    }

    public void setSeries(String title, String unit, int color, List<HistoryStore.Point> points, int indexMode) {
        this.title = title == null ? "" : title;
        this.unit = unit == null ? "" : unit;
        linePaint.setColor(color);
        fillPaint.setColor((color & 0x00FFFFFF) | 0x1A000000);
        values.clear();
        times.clear();
        if (points != null) {
            for (HistoryStore.Point point : points) {
                times.add(point.time);
                if (indexMode == 0) {
                    values.add((float) point.balance);
                } else if (indexMode == 1) {
                    values.add((float) point.usage);
                } else {
                    values.add((float) point.tokens);
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) {
            return;
        }

        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = left + width;
        float bottom = top + height;

        float titleBaseline = top + dp(12);
        float valueBaseline = top + dp(25);
        float chartTop = top + dp(33);
        float chartBottom = bottom - dp(16);

        if (!title.isEmpty()) {
            labelPaint.setTextSize(dp(11));
            labelPaint.setColor(Color.rgb(161, 161, 170));
            canvas.drawText(title, left, titleBaseline, labelPaint);
        }

        if (values.isEmpty()) {
            labelPaint.setColor(Color.rgb(113, 113, 122));
            canvas.drawText("暂无数据", left, (chartTop + chartBottom) / 2, labelPaint);
            return;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : values) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        if (max - min < 0.001f) {
            max = min + 1f;
        }
        float range = max - min;
        float usable = chartBottom - chartTop;

        for (int line = 0; line <= 3; line++) {
            float y = chartTop + usable * line / 3f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        Path linePath = new Path();
        Path fillPath = new Path();
        float stepX = values.size() == 1 ? 0 : (right - left) / (float) (values.size() - 1);
        for (int index = 0; index < values.size(); index++) {
            float x = values.size() == 1 ? (left + right) / 2f : left + stepX * index;
            float normalized = (values.get(index) - min) / range;
            float y = chartBottom - normalized * usable;
            if (index == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartBottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(values.size() == 1 ? (left + right) / 2f : right, chartBottom);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        labelPaint.setTextSize(dp(10));
        labelPaint.setColor(Color.rgb(161, 161, 170));
        String maxLabel = formatValue(max);
        String minLabel = formatValue(min);
        canvas.drawText(maxLabel, left, valueBaseline, labelPaint);
        canvas.drawText(minLabel, left, chartBottom + dp(12), labelPaint);

        String latest = formatValue(values.get(values.size() - 1)) + (unit.isEmpty() ? "" : " " + unit);
        float latestWidth = labelPaint.measureText(latest);
        canvas.drawText(latest, right - latestWidth, valueBaseline, labelPaint);
    }

    private String formatValue(float value) {
        if (Math.abs(value) >= 10000) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (Math.abs(value) >= 100) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
