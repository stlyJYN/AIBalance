package com.deepseek.balance;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public final class BalanceWidget2x1Provider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetUpdater.updateProvider(context, manager, BalanceWidget2x1Provider.class, R.layout.widget_2x1);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetRefreshScheduler.schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        if (!WidgetUpdater.hasWidgets(context)) {
            WidgetRefreshScheduler.cancel(context);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        WidgetUpdater.updateAll(context);
    }
}
