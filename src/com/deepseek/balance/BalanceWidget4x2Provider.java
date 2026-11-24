package com.deepseek.balance;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public final class BalanceWidget4x2Provider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetUpdater.updateProvider(context, manager, BalanceWidget4x2Provider.class, R.layout.widget_4x2);
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
