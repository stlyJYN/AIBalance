package com.deepseek.balance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WidgetRefreshReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WidgetRefreshScheduler.schedule(context);
            WidgetRefreshScheduler.scheduleMidnight(context);
            return;
        }

        final boolean midnight = WidgetRefreshScheduler.ACTION_MIDNIGHT.equals(action);
        if (!midnight && !WidgetRefreshScheduler.ACTION_REFRESH.equals(action)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (midnight || WidgetUpdater.hasWidgets(appContext)) {
                    refresh(appContext);
                }
            } finally {
                if (midnight) {
                    WidgetRefreshScheduler.scheduleMidnight(appContext);
                } else if (WidgetUpdater.hasWidgets(appContext)) {
                    WidgetRefreshScheduler.schedule(appContext);
                }
                pendingResult.finish();
            }
        });
    }

    private static void refresh(Context context) {
        ProviderConfig config = selectedProvider(context);
        if (config == null) {
            return;
        }
        String apiKey = ProviderConfig.getApiKey(context, config.id);
        if (apiKey.isEmpty()) {
            return;
        }
        try {
            ProviderApi.BalanceResult result = ProviderApi.query(config, apiKey);
            String usage = UsageTracker.recordBalance(context, config.id, result.rawBalance);
            HistoryStore.record(
                    context,
                    config.id,
                    parseBalance(result.rawBalance),
                    UsageTracker.lastUsageMoney(context, config.id),
                    result.tokens);
            WidgetUpdater.saveSnapshot(context, config, config.symbol + result.rawBalance, usage);
        } catch (Exception ignored) {
        }
    }

    private static ProviderConfig selectedProvider(Context context) {
        java.util.List<ProviderConfig> providers = ProviderConfig.ensureBuiltins(context);
        return ProviderConfig.find(providers, ProviderConfig.getSelectedId(context));
    }

    private static double parseBalance(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception exception) {
            return 0;
        }
    }
}
