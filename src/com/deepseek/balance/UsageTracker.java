package com.deepseek.balance;

import android.content.Context;
import android.content.SharedPreferences;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tracks today's cumulative consumption per provider by subtracting successive
 * balance readings. This is an estimate: it only counts decreases, so top-ups
 * reset the day baseline rather than being treated as negative usage.
 */
final class UsageTracker {
    private UsageTracker() {
    }

    static String recordBalance(Context context, String providerId, String rawBalance) {
        BigDecimal current;
        try {
            current = new BigDecimal(rawBalance).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception exception) {
            return "—";
        }

        SharedPreferences preferences = context.getSharedPreferences(
                ProviderConfig.PREFS_NAME, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String savedDate = preferences.getString(dateKey(providerId), "");
        String lastRaw = preferences.getString(lastKey(providerId), "");
        String totalRaw = preferences.getString(totalKey(providerId), "0.00");
        BigDecimal total;

        try {
            total = new BigDecimal(totalRaw).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception exception) {
            total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (!today.equals(savedDate) || lastRaw.isEmpty()) {
            total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            try {
                BigDecimal last = new BigDecimal(lastRaw).setScale(2, RoundingMode.HALF_UP);
                if (current.compareTo(last) < 0) {
                    total = total.add(last.subtract(current)).setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception ignored) {
            }
        }

        preferences.edit()
                .putString(dateKey(providerId), today)
                .putString(lastKey(providerId), current.toPlainString())
                .putString(totalKey(providerId), total.toPlainString())
                .apply();
        return "¥" + total.toPlainString();
    }

    static double lastUsageMoney(Context context, String providerId) {
        SharedPreferences preferences = context.getSharedPreferences(
                ProviderConfig.PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(totalKey(providerId), "0.00");
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).doubleValue();
        } catch (Exception exception) {
            return 0;
        }
    }

    private static String dateKey(String providerId) {
        return "usage_date_" + providerId;
    }

    private static String lastKey(String providerId) {
        return "usage_last_balance_" + providerId;
    }

    private static String totalKey(String providerId) {
        return "usage_total_" + providerId;
    }
}
