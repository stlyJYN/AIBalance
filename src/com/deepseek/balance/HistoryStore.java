package com.deepseek.balance;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Keeps one sample per day per provider and only retains the most recent
 * fifteen days. Successive refreshes on the same day update the day's point
 * instead of appending, so the trend chart stays a clean daily curve.
 */
final class HistoryStore {
    private static final int MAX_DAYS = 15;
    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");

    private HistoryStore() {
    }

    static final class Point {
        final String day;
        final long time;
        final double balance;
        final double usage;
        final long tokens;

        Point(String day, long time, double balance, double usage, long tokens) {
            this.day = day;
            this.time = time;
            this.balance = balance;
            this.usage = usage;
            this.tokens = tokens;
        }
    }

    static void record(Context context, String providerId, double balance, double usage, long tokens) {
        List<Point> points = load(context, providerId);
        String today = dayKey(System.currentTimeMillis());
        if (!points.isEmpty() && today.equals(points.get(points.size() - 1).day)) {
            points.remove(points.size() - 1);
        }
        points.add(new Point(today, System.currentTimeMillis(), balance, usage, tokens));
        while (points.size() > MAX_DAYS) {
            points.remove(0);
        }
        save(context, providerId, points);
    }

    static List<Point> load(Context context, String providerId) {
        List<Point> result = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences(ProviderConfig.PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(historyKey(providerId), "");
        if (raw.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                String day = object.optString("d", "");
                long time = object.optLong("t", 0L);
                if (day.isEmpty()) {
                    day = time > 0L ? dayKey(time) : "";
                }
                if (day.isEmpty()) {
                    continue;
                }
                double balance = object.optDouble("b", 0);
                double usage = object.optDouble("u", 0);
                long tokens = object.optLong("k", -1L);
                result.add(new Point(day, time, balance, usage, tokens));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void save(Context context, String providerId, List<Point> points) {
        JSONArray array = new JSONArray();
        for (Point point : points) {
            JSONObject object = new JSONObject();
            try {
                object.put("d", point.day);
                object.put("t", point.time);
                object.put("b", point.balance);
                object.put("u", point.usage);
                object.put("k", point.tokens);
            } catch (Exception ignored) {
            }
            array.put(object);
        }
        context.getSharedPreferences(ProviderConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(historyKey(providerId), array.toString())
                .apply();
    }

    private static String historyKey(String providerId) {
        return "history_" + providerId;
    }

    private static String dayKey(long millis) {
        Calendar calendar = Calendar.getInstance(BEIJING);
        calendar.setTimeInMillis(millis);
        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}
