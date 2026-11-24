package com.deepseek.balance;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;

final class WidgetUpdater {
    static final String PREFS_NAME = "deepseek_balance";
    static final String PREF_API_KEY = "api_key";
    static final String PREF_BALANCE = "widget_balance";
    static final String PREF_USAGE = "widget_usage";
    static final String PREF_PROVIDER_NAME = "widget_provider_name";
    static final String PREF_PROVIDER_PEAK = "widget_provider_peak";
    static final String PREF_BACKGROUND_REFRESH_INTERVAL = "widget_refresh_interval_ms";
    static final long DEFAULT_BACKGROUND_REFRESH_MS = 600000L;

    private WidgetUpdater() {
    }

    static String getApiKey(Context context, ProviderConfig config) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(ProviderConfig.apiKeyPrefKey(config.id), "").trim();
    }

    static void saveSnapshot(Context context, ProviderConfig config, String balance, String usage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_BALANCE, balance == null ? "—" : balance)
                .putString(PREF_USAGE, usage == null ? "—" : usage)
                .putString(PREF_PROVIDER_NAME, config == null ? "" : config.name)
                .putBoolean(PREF_PROVIDER_PEAK, config != null && config.peakEnabled)
                .apply();
        updateAll(context);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateProvider(context, manager, BalanceWidget4x2Provider.class, R.layout.widget_4x2);
        updateProvider(context, manager, BalanceWidget2x1Provider.class, R.layout.widget_2x1);
        updateProvider(context, manager, BalanceWidget2x2Provider.class, R.layout.widget_2x2);
    }

    static void updateProvider(
            Context context,
            AppWidgetManager manager,
            Class<? extends AppWidgetProvider> providerClass,
            int layoutId
    ) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids == null || ids.length == 0) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String balance = preferences.getString(PREF_BALANCE, "—");
        String usage = preferences.getString(PREF_USAGE, "—");
        String providerName = preferences.getString(PREF_PROVIDER_NAME, "");
        boolean peakEnabled = preferences.getBoolean(PREF_PROVIDER_PEAK, false);
        PeakTimeUtils.Status peakStatus = peakEnabled ? PeakTimeUtils.currentStatus(context) : null;
        PendingIntent openApp = openAppPendingIntent(context);

        for (int appWidgetId : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
            views.setTextViewText(R.id.widget_balance, balance);
            views.setTextViewText(R.id.widget_usage, usage);
            if (layoutId == R.layout.widget_4x2 || layoutId == R.layout.widget_2x2) {
                if (peakEnabled && peakStatus != null) {
                    views.setTextViewText(R.id.widget_peak_label, "当前时段");
                    views.setTextViewText(R.id.widget_peak_status, peakStatus.title);
                    views.setTextColor(R.id.widget_peak_status,
                            peakStatus.peak ? Color.rgb(220, 38, 38) : Color.rgb(161, 161, 170));
                } else {
                    views.setTextViewText(R.id.widget_peak_label, "当前模型");
                    views.setTextViewText(R.id.widget_peak_status, providerName);
                    views.setTextColor(R.id.widget_peak_status, Color.rgb(161, 161, 170));
                }
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp);
            manager.updateAppWidget(appWidgetId, views);
        }
    }

    static boolean hasWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] wide = manager.getAppWidgetIds(new ComponentName(context, BalanceWidget4x2Provider.class));
        int[] small = manager.getAppWidgetIds(new ComponentName(context, BalanceWidget2x1Provider.class));
        int[] square = manager.getAppWidgetIds(new ComponentName(context, BalanceWidget2x2Provider.class));
        return (wide != null && wide.length > 0) || (small != null && small.length > 0) || (square != null && square.length > 0);
    }

    private static PendingIntent openAppPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
