package com.deepseek.balance;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class WidgetRefreshScheduler {
    static final String ACTION_REFRESH = "com.deepseek.balance.action.WIDGET_REFRESH";
    static final String ACTION_MIDNIGHT = "com.deepseek.balance.action.MIDNIGHT_REFRESH";

    private static final int REFRESH_REQUEST_CODE = 7101;
    private static final int MIDNIGHT_REQUEST_CODE = 7102;

    private WidgetRefreshScheduler() {
    }

    static void schedule(Context context) {
        if (!WidgetUpdater.hasWidgets(context)) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + getInterval(context),
                pendingIntent(context, ACTION_REFRESH, REFRESH_REQUEST_CODE));
    }

    static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, ACTION_REFRESH, REFRESH_REQUEST_CODE));
        }
    }

    static void scheduleMidnight(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAt = nextMidnight();
        PendingIntent pendingIntent = pendingIntent(context, ACTION_MIDNIGHT, MIDNIGHT_REQUEST_CODE);
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private static long nextMidnight() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long getInterval(Context context) {
        return context.getSharedPreferences(WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(WidgetUpdater.PREF_BACKGROUND_REFRESH_INTERVAL, WidgetUpdater.DEFAULT_BACKGROUND_REFRESH_MS);
    }

    private static PendingIntent pendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, WidgetRefreshReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
