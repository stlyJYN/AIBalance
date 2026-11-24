package com.deepseek.balance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PLATFORM_URL = "https://platform.deepseek.com/";
    private static final long DEFAULT_REFRESH_MS = 15000L;
    private static final String PREF_REFRESH_INTERVAL = "refresh_interval_ms";
    private static final long[] REFRESH_INTERVALS = {1000L, 5000L, 10000L, 15000L, 30000L, 60000L, 300000L, 600000L, 900000L};
    private static final String[] REFRESH_LABELS = {"1 秒", "5 秒", "10 秒", "15 秒", "30 秒", "1 分钟", "5 分钟", "10 分钟", "15 分钟"};
    private static final long[] BACKGROUND_REFRESH_INTERVALS = {1000L, 5000L, 10000L, 30000L, 60000L, 300000L, 600000L, 1800000L, 3600000L};
    private static final String[] BACKGROUND_REFRESH_LABELS = {"1 秒", "5 秒", "10 秒", "30 秒", "1 分钟", "5 分钟", "10 分钟", "30 分钟", "1 小时"};

    private static final int COLOR_BG = Color.rgb(11, 11, 12);
    private static final int COLOR_CARD = Color.rgb(20, 20, 22);
    private static final int COLOR_INPUT = Color.rgb(15, 15, 17);
    private static final int COLOR_BORDER = Color.rgb(45, 45, 49);
    private static final int COLOR_TEXT = Color.rgb(245, 245, 247);
    private static final int COLOR_MUTED = Color.rgb(161, 161, 170);
    private static final int COLOR_HINT = Color.rgb(113, 113, 122);
    private static final int COLOR_BUTTON = Color.rgb(229, 231, 235);
    private static final int COLOR_BUTTON_TEXT = Color.rgb(15, 15, 17);
    private static final int COLOR_STATUS = Color.rgb(39, 39, 42);
    private static final int COLOR_PEAK = Color.rgb(220, 38, 38);
    private static final int COLOR_PEAK_BG = Color.rgb(45, 18, 21);
    private static final int COLOR_PEAK_BORDER = Color.rgb(127, 29, 29);
    private static final int COLOR_USAGE = Color.rgb(245, 158, 11);
    private static final int COLOR_TOKEN = Color.rgb(59, 130, 246);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) {
                return;
            }
            updatePeakCard();
            ProviderConfig config = selectedProvider();
            if (!loading && config != null && !TextUtils.isEmpty(keyInput.getText().toString().trim())) {
                queryBalance(true);
            } else {
                scheduleAutoRefresh(refreshIntervalMs);
            }
        }
    };

    private final Runnable secondTickRunnable = new Runnable() {
        @Override
        public void run() {
            updatePeakCard();
            scheduleSecondTick();
        }
    };

    private final List<ProviderConfig> providers = new ArrayList<>();
    private String selectedProviderId = "deepseek";

    private SharedPreferences preferences;
    private LinearLayout providerRow;
    private EditText keyInput;
    private CheckBox rememberKey;
    private TextView queryButton;
    private TextView statusView;
    private TextView balanceView;
    private TextView todayUsageView;
    private TextView todayTokensView;
    private TextView detailsView;
    private TextView autoStatusView;
    private LinearLayout peakCard;
    private TextView peakTitleView;
    private TextView peakRemainingView;
    private TextView peakTimeView;
    private TrendChartView balanceChart;
    private TrendChartView usageChart;
    private TrendChartView tokenChart;
    private LinearLayout tokenChartWrap;
    private TextView platformButton;

    private long refreshIntervalMs = DEFAULT_REFRESH_MS;
    private long backgroundRefreshIntervalMs = WidgetUpdater.DEFAULT_BACKGROUND_REFRESH_MS;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(ProviderConfig.PREFS_NAME, MODE_PRIVATE);
        refreshIntervalMs = preferences.getLong(PREF_REFRESH_INTERVAL, DEFAULT_REFRESH_MS);
        backgroundRefreshIntervalMs = preferences.getLong(WidgetUpdater.PREF_BACKGROUND_REFRESH_INTERVAL, WidgetUpdater.DEFAULT_BACKGROUND_REFRESH_MS);

        providers.clear();
        providers.addAll(ProviderConfig.ensureBuiltins(this));
        selectedProviderId = ProviderConfig.getSelectedId(this);
        if (ProviderConfig.find(providers, selectedProviderId) == null) {
            selectedProviderId = "deepseek";
            ProviderConfig.setSelectedId(this, selectedProviderId);
        }

        WidgetRefreshScheduler.scheduleMidnight(this);
        configureWindow();
        buildInterface();
        rebuildProviderChips();
        loadSavedKey();
        updatePeakCard();
        refreshCharts();
        HolidayUpdater.updateIfNeeded(this, changed -> runOnUiThread(this::updatePeakCard));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(COLOR_BG);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= 28) {
            window.setNavigationBarDividerColor(COLOR_BG);
        }
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setPadding(dp(20), dp(22), dp(20), dp(22));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView kicker = text("AI API 余额查询", 11, COLOR_MUTED, Typeface.BOLD);
        kicker.setLetterSpacing(0.18f);
        content.addView(kicker, matchWrap(0));

        TextView title = text("余额查询", 31, COLOR_TEXT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        content.addView(title, matchWrap(8));

        TextView subtitle = text("支持 DeepSeek、小米 MiMo、智谱与自定义服务商", 14, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setLineSpacing(0f, 1.15f);
        content.addView(subtitle, matchWrap(8));

        LinearLayout providerCard = card();
        content.addView(providerCard, matchWrap(20));

        TextView providerLabel = text("服务商", 11, COLOR_MUTED, Typeface.BOLD);
        providerLabel.setLetterSpacing(0.12f);
        providerCard.addView(providerLabel, matchWrap(0));

        HorizontalScrollView providerScroll = new HorizontalScrollView(this);
        providerScroll.setHorizontalScrollBarEnabled(false);
        providerScroll.setPadding(0, dp(12), 0, 0);
        providerCard.addView(providerScroll, matchWrap(0));

        providerRow = new LinearLayout(this);
        providerRow.setOrientation(LinearLayout.HORIZONTAL);
        providerScroll.addView(providerRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout keyCard = card();
        content.addView(keyCard, matchWrap(16));

        TextView keyLabel = text("API KEY", 11, COLOR_MUTED, Typeface.BOLD);
        keyLabel.setLetterSpacing(0.12f);
        keyCard.addView(keyLabel, matchWrap(0));

        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(15);
        keyInput.setTextColor(COLOR_TEXT);
        keyInput.setHintTextColor(COLOR_HINT);
        keyInput.setHint("sk-... / 自定义密钥");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        keyInput.setBackground(roundRect(COLOR_INPUT, COLOR_BORDER, 12, 1));
        keyInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        keyInput.setSelectAllOnFocus(false);
        if (Build.VERSION.SDK_INT >= 26) {
            keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        keyCard.addView(keyInput, matchWrap(12));

        LinearLayout rememberRow = new LinearLayout(this);
        rememberRow.setOrientation(LinearLayout.HORIZONTAL);
        rememberRow.setGravity(Gravity.CENTER_VERTICAL);
        keyCard.addView(rememberRow, matchWrap(8));

        rememberKey = new CheckBox(this);
        rememberKey.setText("记住密钥");
        rememberKey.setTextSize(13);
        rememberKey.setTextColor(COLOR_MUTED);
        rememberKey.setButtonTintList(ColorStateList.valueOf(COLOR_BUTTON));
        rememberRow.addView(rememberKey, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView localOnly = text("仅存本机", 12, COLOR_HINT, Typeface.NORMAL);
        rememberRow.addView(localOnly, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        queryButton = actionButton("查询余额", true);
        keyCard.addView(queryButton, matchHeight(50, 18));

        autoStatusView = text("进入自动查询 · 每 " + refreshIntervalLabel() + "刷新", 11, COLOR_HINT, Typeface.NORMAL);
        autoStatusView.setGravity(Gravity.CENTER);
        autoStatusView.setClickable(true);
        autoStatusView.setFocusable(true);
        autoStatusView.setContentDescription("点击更换前台刷新频率，长按更换后台刷新频率");
        autoStatusView.setOnClickListener(v -> showRefreshIntervalDialog());
        autoStatusView.setOnLongClickListener(v -> {
            showBackgroundRefreshDialog();
            return true;
        });
        keyCard.addView(autoStatusView, matchWrap(12));

        LinearLayout resultCard = card();
        content.addView(resultCard, matchWrap(16));

        statusView = text("等待查询", 11, COLOR_MUTED, Typeface.BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setLetterSpacing(0.08f);
        statusView.setBackground(roundRect(COLOR_STATUS, COLOR_BORDER, 99, 1));
        statusView.setPadding(dp(12), dp(7), dp(12), dp(7));
        resultCard.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout balanceRow = new LinearLayout(this);
        balanceRow.setOrientation(LinearLayout.HORIZONTAL);
        balanceRow.setGravity(Gravity.CENTER_VERTICAL);
        resultCard.addView(balanceRow, matchWrap(16));

        LinearLayout balanceColumn = new LinearLayout(this);
        balanceColumn.setOrientation(LinearLayout.VERTICAL);
        balanceRow.addView(balanceColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView balanceLabel = text("余额", 11, COLOR_HINT, Typeface.NORMAL);
        balanceColumn.addView(balanceLabel, matchWrap(0));

        balanceView = text("—", 26, COLOR_TEXT, Typeface.BOLD);
        balanceView.setIncludeFontPadding(false);
        balanceView.setLetterSpacing(-0.02f);
        balanceColumn.addView(balanceView, matchWrap(4));

        LinearLayout usageColumn = new LinearLayout(this);
        usageColumn.setOrientation(LinearLayout.VERTICAL);
        usageColumn.setGravity(Gravity.END);
        balanceRow.addView(usageColumn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView usageLabel = text("今日消耗", 11, COLOR_MUTED, Typeface.NORMAL);
        usageLabel.setGravity(Gravity.END);
        usageColumn.addView(usageLabel, matchWrap(0));

        todayUsageView = text("—", 26, COLOR_TEXT, Typeface.BOLD);
        todayUsageView.setGravity(Gravity.END);
        usageColumn.addView(todayUsageView, matchWrap(4));

        todayTokensView = text("今日 Token —", 12, COLOR_MUTED, Typeface.NORMAL);
        todayTokensView.setGravity(Gravity.END);
        resultCard.addView(todayTokensView, matchWrap(8));

        detailsView = text("等待读取余额", 13, COLOR_MUTED, Typeface.NORMAL);
        detailsView.setLineSpacing(dp(4), 1.1f);
        resultCard.addView(detailsView, matchWrap(12));

        LinearLayout chartCard = card();
        content.addView(chartCard, matchWrap(16));

        TextView chartLabel = text("消耗走势（本地记录）", 11, COLOR_MUTED, Typeface.BOLD);
        chartLabel.setLetterSpacing(0.12f);
        chartCard.addView(chartLabel, matchWrap(0));

        balanceChart = new TrendChartView(this);
        chartCard.addView(balanceChart, matchHeight(112, 12));

        usageChart = new TrendChartView(this);
        chartCard.addView(usageChart, matchHeight(112, 10));

        tokenChartWrap = new LinearLayout(this);
        tokenChartWrap.setOrientation(LinearLayout.VERTICAL);
        chartCard.addView(tokenChartWrap, matchWrap(10));

        tokenChart = new TrendChartView(this);
        tokenChartWrap.addView(tokenChart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(112)));

        peakCard = card();
        content.addView(peakCard, matchWrap(16));

        TextView peakLabel = text("谷峰时段", 11, COLOR_MUTED, Typeface.BOLD);
        peakLabel.setLetterSpacing(0.12f);
        peakCard.addView(peakLabel, matchWrap(0));

        LinearLayout peakStatusRow = new LinearLayout(this);
        peakStatusRow.setOrientation(LinearLayout.HORIZONTAL);
        peakStatusRow.setGravity(Gravity.CENTER_VERTICAL);
        peakCard.addView(peakStatusRow, matchWrap(10));

        peakTitleView = text("—", 20, COLOR_TEXT, Typeface.BOLD);
        peakStatusRow.addView(peakTitleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout remainingColumn = new LinearLayout(this);
        remainingColumn.setOrientation(LinearLayout.VERTICAL);
        remainingColumn.setGravity(Gravity.END);
        remainingColumn.setMinimumWidth(dp(92));
        peakStatusRow.addView(remainingColumn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView remainingLabel = text("剩余时间", 11, COLOR_MUTED, Typeface.NORMAL);
        remainingLabel.setGravity(Gravity.END);
        remainingColumn.addView(remainingLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        peakRemainingView = text("", 20, COLOR_MUTED, Typeface.BOLD);
        peakRemainingView.setGravity(Gravity.END);
        peakRemainingView.setSingleLine(true);
        peakRemainingView.setMinWidth(dp(92));
        LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        remainingParams.topMargin = dp(2);
        remainingColumn.addView(peakRemainingView, remainingParams);

        peakTimeView = text("", 11, COLOR_HINT, Typeface.NORMAL);
        peakCard.addView(peakTimeView, matchWrap(4));

        platformButton = actionButton("进入 DeepSeek 开放平台  ↗", false);
        platformButton.setContentDescription("打开 DeepSeek 开放平台");
        content.addView(platformButton, matchHeight(52, 16));

        TextView privacy = text(
                "密钥仅发送给所选服务商的余额接口。勾选“记住密钥”后，密钥只保存在本机应用私有存储中。",
                11,
                COLOR_HINT,
                Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(dp(2), 1.15f);
        content.addView(privacy, matchWrap(18));

        setContentView(scroll);
        applyInsets(scroll);

        queryButton.setOnClickListener(v -> queryBalance(false));
        platformButton.setOnClickListener(v -> openPlatform());
        keyInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                queryBalance(false);
                return true;
            }
            return false;
        });
        keyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (rememberKey.isChecked()) {
                    ProviderConfig.saveApiKey(MainActivity.this, selectedProviderId, s.toString());
                }
            }
        });
        rememberKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ProviderConfig.saveApiKey(MainActivity.this, selectedProviderId, keyInput.getText().toString());
            } else {
                ProviderConfig.saveApiKey(MainActivity.this, selectedProviderId, "");
            }
        });
    }

    private void applyInsets(View root) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(dp(20), dp(22) + bars.top, dp(20), dp(22) + bars.bottom);
                return windowInsets;
            });
            root.requestApplyInsets();
        }
    }

    private void rebuildProviderChips() {
        if (providerRow == null) {
            return;
        }
        providerRow.removeAllViews();
        for (ProviderConfig provider : providers) {
            TextView chip = text(provider.name, 13,
                    provider.id.equals(selectedProviderId) ? COLOR_BUTTON_TEXT : COLOR_MUTED,
                    provider.id.equals(selectedProviderId) ? Typeface.BOLD : Typeface.NORMAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(roundRect(
                    provider.id.equals(selectedProviderId) ? COLOR_BUTTON : COLOR_INPUT,
                    COLOR_BORDER, 99, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            providerRow.addView(chip, params);
            chip.setOnClickListener(v -> selectProvider(provider.id));
            if (ProviderConfig.TYPE_CUSTOM.equals(provider.type)) {
                chip.setOnLongClickListener(v -> {
                    confirmDeleteProvider(provider);
                    return true;
                });
            }
        }

        TextView addChip = text("＋ 添加", 13, COLOR_MUTED, Typeface.BOLD);
        addChip.setGravity(Gravity.CENTER);
        addChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        addChip.setBackground(roundRect(COLOR_INPUT, COLOR_BORDER, 99, 1));
        providerRow.addView(addChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addChip.setOnClickListener(v -> showAddProviderDialog());
    }

    private void selectProvider(String providerId) {
        if (providerId.equals(selectedProviderId)) {
            return;
        }
        selectedProviderId = providerId;
        ProviderConfig.setSelectedId(this, providerId);
        rebuildProviderChips();
        loadSavedKey();
        updatePeakCard();
        refreshCharts();
        resetResultCard();
        ProviderConfig config = selectedProvider();
        if (config != null && !TextUtils.isEmpty(ProviderConfig.getApiKey(this, providerId))) {
            queryBalance(true);
        }
    }

    private void resetResultCard() {
        statusView.setText("等待查询");
        statusView.setTextColor(COLOR_MUTED);
        statusView.setBackground(roundRect(COLOR_STATUS, COLOR_BORDER, 99, 1));
        balanceView.setText("—");
        todayUsageView.setText("—");
        todayTokensView.setText("今日 Token —");
        detailsView.setVisibility(View.VISIBLE);
        detailsView.setText("等待读取余额");
        autoStatusView.setText("每 " + refreshIntervalLabel() + "刷新");
    }

    private void loadSavedKey() {
        String savedKey = ProviderConfig.getApiKey(this, selectedProviderId);
        keyInput.setText(savedKey);
        keyInput.setSelection(savedKey.length());
        rememberKey.setChecked(!savedKey.isEmpty());
    }

    private ProviderConfig selectedProvider() {
        return ProviderConfig.find(providers, selectedProviderId);
    }

    private void queryBalance(boolean automatic) {
        if (loading) {
            return;
        }
        ProviderConfig config = selectedProvider();
        if (config == null) {
            return;
        }

        String apiKey = keyInput.getText().toString().trim();
        if (TextUtils.isEmpty(apiKey)) {
            if (automatic) {
                scheduleAutoRefresh(refreshIntervalMs);
                return;
            }
            Toast.makeText(this, "请先输入 " + config.name + " 的 API Key", Toast.LENGTH_SHORT).show();
            keyInput.requestFocus();
            return;
        }

        keyInput.clearFocus();
        autoStatusView.setText(automatic ? "正在自动查询…" : "正在查询…");
        setLoading(true);
        scheduleAutoRefresh(refreshIntervalMs);

        executor.execute(() -> {
            try {
                ProviderApi.BalanceResult result = ProviderApi.query(config, apiKey);
                runOnUiThread(() -> showBalance(config, result));
            } catch (ProviderApi.ApiException exception) {
                String message = exception.getMessage() == null ? "请求失败" : exception.getMessage();
                runOnUiThread(() -> showError(message));
            } catch (Exception exception) {
                final String message;
                if (exception instanceof java.io.IOException) {
                    message = "网络连接失败，请检查网络后重试";
                } else if (exception.getMessage() != null && exception.getMessage().contains("余额")) {
                    message = exception.getMessage();
                } else {
                    message = "查询失败，请稍后重试";
                }
                runOnUiThread(() -> showError(message));
            }
        });
    }

    private void showBalance(ProviderConfig config, ProviderApi.BalanceResult result) {
        setLoading(false);
        String usage = UsageTracker.recordBalance(this, config.id, result.rawBalance);
        double balanceValue = parseBalance(result.rawBalance);
        double usageValue = UsageTracker.lastUsageMoney(this, config.id);
        HistoryStore.record(this, config.id, balanceValue, usageValue, result.tokens);

        statusView.setText("查询成功");
        statusView.setTextColor(COLOR_TEXT);
        statusView.setBackground(roundRect(COLOR_STATUS, COLOR_BORDER, 99, 1));
        balanceView.setText(formatAmount(result.rawBalance, config));
        todayUsageView.setText(usage);
        todayTokensView.setText(result.tokens >= 0 ? "今日 Token " + formatNumber(result.tokens) : "今日 Token —");
        detailsView.setVisibility(View.GONE);
        WidgetUpdater.saveSnapshot(this, config, formatAmount(result.rawBalance, config), usage);
        autoStatusView.setText("每 " + refreshIntervalLabel() + "刷新 · 更新于 " + currentTime());
        refreshCharts();
    }

    private void showError(String message) {
        setLoading(false);
        statusView.setText("查询失败");
        statusView.setTextColor(COLOR_TEXT);
        statusView.setBackground(roundRect(COLOR_STATUS, COLOR_BORDER, 99, 1));
        balanceView.setText("—");
        todayUsageView.setText("—");
        todayTokensView.setText("今日 Token —");
        detailsView.setVisibility(View.VISIBLE);
        detailsView.setText(message);
        autoStatusView.setText("每 " + refreshIntervalLabel() + "刷新 · 本次失败，稍后重试");
    }

    private void refreshCharts() {
        if (balanceChart == null) {
            return;
        }
        ProviderConfig config = selectedProvider();
        List<HistoryStore.Point> points = HistoryStore.load(this, selectedProviderId);
        String symbol = config == null ? "¥" : config.symbol;
        balanceChart.setSeries("余额", symbol, COLOR_TEXT, points, 0);
        usageChart.setSeries("今日消耗", symbol, COLOR_USAGE, points, 1);

        boolean supportsTokens = config != null && !config.tokensPath.isEmpty();
        if (supportsTokens) {
            tokenChartWrap.setVisibility(View.VISIBLE);
            tokenChart.setSeries("今日 Token", "", COLOR_TOKEN, points, 2);
        } else {
            tokenChartWrap.setVisibility(View.GONE);
        }
        boolean showPeak = config != null && config.peakEnabled;
        peakCard.setVisibility(showPeak ? View.VISIBLE : View.GONE);
        platformButton.setVisibility(ProviderConfig.TYPE_DEEPSEEK.equals(config == null ? "" : config.type) ? View.VISIBLE : View.GONE);
    }

    private double parseBalance(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception exception) {
            return 0;
        }
    }

    private String refreshIntervalLabel() {
        for (int index = 0; index < REFRESH_INTERVALS.length; index++) {
            if (REFRESH_INTERVALS[index] == refreshIntervalMs) {
                return REFRESH_LABELS[index];
            }
        }
        return REFRESH_LABELS[3];
    }

    private void showRefreshIntervalDialog() {
        int selected = 3;
        for (int index = 0; index < REFRESH_INTERVALS.length; index++) {
            if (REFRESH_INTERVALS[index] == refreshIntervalMs) {
                selected = index;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("刷新频率")
                .setSingleChoiceItems(REFRESH_LABELS, selected, (dialog, which) -> {
                    refreshIntervalMs = REFRESH_INTERVALS[which];
                    preferences.edit().putLong(PREF_REFRESH_INTERVAL, refreshIntervalMs).apply();
                    autoStatusView.setText("每 " + refreshIntervalLabel() + "刷新");
                    scheduleAutoRefresh(refreshIntervalMs);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBackgroundRefreshDialog() {
        int selected = 6;
        for (int index = 0; index < BACKGROUND_REFRESH_INTERVALS.length; index++) {
            if (BACKGROUND_REFRESH_INTERVALS[index] == backgroundRefreshIntervalMs) {
                selected = index;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("后台查询频率")
                .setSingleChoiceItems(BACKGROUND_REFRESH_LABELS, selected, (dialog, which) -> {
                    backgroundRefreshIntervalMs = BACKGROUND_REFRESH_INTERVALS[which];
                    preferences.edit().putLong(WidgetUpdater.PREF_BACKGROUND_REFRESH_INTERVAL, backgroundRefreshIntervalMs).apply();
                    Toast.makeText(this, "后台查询频率：" + BACKGROUND_REFRESH_LABELS[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddProviderDialog() {
        ProviderConfig template = ProviderConfig.customTemplate("custom_" + System.currentTimeMillis());
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(24), dp(16), dp(24), 0);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText nameInput = field(form, "名称", template.name, false);
        EditText urlInput = field(form, "余额接口 URL（支持 {key} 占位）", template.balanceUrl, false);
        EditText headerInput = field(form, "鉴权头名称（如 Authorization / api-key）", template.authHeaderName, false);
        EditText headerValueInput = field(form, "鉴权头值（{key} 会被替换为密钥）", template.authValueTemplate, false);
        EditText pathInput = field(form, "余额 JSON 路径", template.balancePath, false);
        EditText symbolInput = field(form, "币种符号（如 ¥ / $）", template.symbol, false);
        EditText usageUrlInput = field(form, "用量接口 URL（可选，支持 {today}）", template.usageUrl, true);
        EditText tokensPathInput = field(form, "Token JSON 路径（可选，如 sum(data.total_tokens)）", template.tokensPath, true);

        TextView hint = text(
                "余额路径示例：balance_infos[currency=CNY].total_balance、data.balance。\n"
                        + "可用 data.availableBalance|data.balance 表示多个候选。",
                11, COLOR_HINT, Typeface.NORMAL);
        hint.setLineSpacing(dp(2), 1.15f);
        form.addView(hint, matchWrap(8));

        new AlertDialog.Builder(this)
                .setTitle("添加自定义服务商")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String url = urlInput.getText().toString().trim();
                    if (name.isEmpty() || url.isEmpty()) {
                        Toast.makeText(this, "名称和余额接口 URL 不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ProviderConfig config = new ProviderConfig(
                            "custom_" + System.currentTimeMillis(),
                            name,
                            ProviderConfig.TYPE_CUSTOM,
                            url,
                            headerInput.getText().toString().trim(),
                            headerValueInput.getText().toString().trim(),
                            pathInput.getText().toString().trim(),
                            "CNY",
                            symbolInput.getText().toString().trim(),
                            false,
                            usageUrlInput.getText().toString().trim(),
                            tokensPathInput.getText().toString().trim());
                    providers.add(config);
                    ProviderConfig.saveAll(this, providers);
                    ProviderConfig.setSelectedId(this, config.id);
                    selectedProviderId = config.id;
                    rebuildProviderChips();
                    loadSavedKey();
                    refreshCharts();
                    resetResultCard();
                    Toast.makeText(this, "已添加 " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(LinearLayout form, String label, String value, boolean optional) {
        TextView labelView = text(label + (optional ? "（可选）" : ""), 11, COLOR_MUTED, Typeface.NORMAL);
        form.addView(labelView, matchWrap(12));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_HINT);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackground(roundRect(COLOR_INPUT, COLOR_BORDER, 10, 1));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(input, matchWrap(0));
        return input;
    }

    private void confirmDeleteProvider(ProviderConfig provider) {
        new AlertDialog.Builder(this)
                .setTitle("删除服务商")
                .setMessage("确定删除「" + provider.name + "」吗？已保存的密钥也会一并清除。")
                .setPositiveButton("删除", (dialog, which) -> {
                    ProviderConfig.deleteProvider(this, provider.id);
                    providers.clear();
                    providers.addAll(ProviderConfig.loadAll(this));
                    selectedProviderId = ProviderConfig.getSelectedId(this);
                    rebuildProviderChips();
                    loadSavedKey();
                    refreshCharts();
                    resetResultCard();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updatePeakCard() {
        if (peakTitleView == null) {
            return;
        }
        ProviderConfig config = selectedProvider();
        if (config == null || !config.peakEnabled) {
            return;
        }
        PeakTimeUtils.Status status = PeakTimeUtils.currentStatus(this);
        peakTitleView.setText(status.title);
        if (status.peak) {
            peakTitleView.setTextColor(COLOR_PEAK);
        } else {
            peakTitleView.setTextColor(COLOR_MUTED);
        }
        peakRemainingView.setText(status.remaining);
        peakRemainingView.setVisibility(status.remaining.isEmpty() ? View.GONE : View.VISIBLE);
        peakTimeView.setText(status.currentTime);
    }

    private void setLoading(boolean value) {
        loading = value;
        queryButton.setEnabled(!value);
        queryButton.setAlpha(value ? 0.55f : 1f);
        keyInput.setEnabled(!value);
        rememberKey.setEnabled(!value);
        if (value) {
            queryButton.setText("查询中…");
            statusView.setText("正在读取");
            statusView.setTextColor(COLOR_MUTED);
            balanceView.setText("…");
            todayUsageView.setText("…");
            detailsView.setVisibility(View.GONE);
            detailsView.setText("");
        } else {
            queryButton.setText("查询余额");
        }
    }

    private void openPlatform() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLATFORM_URL));
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, "未找到可用浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatAmount(String amount, ProviderConfig config) {
        if (TextUtils.isEmpty(amount)) {
            return "—";
        }
        if ("CNY".equalsIgnoreCase(config.currency)) {
            return config.symbol + amount;
        }
        if ("USD".equalsIgnoreCase(config.currency)) {
            return config.symbol + amount;
        }
        return config.symbol + amount;
    }

    private String formatNumber(long number) {
        return String.format(Locale.US, "%,d", number);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(COLOR_CARD, COLOR_BORDER, 18, 1));
        card.setElevation(dp(1));
        return card;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 15, primary ? COLOR_BUTTON_TEXT : COLOR_TEXT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setBackground(primary
                ? roundRect(COLOR_BUTTON, 0, 14, 0)
                : roundRect(COLOR_INPUT, COLOR_BORDER, 14, 1));
        return button;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private GradientDrawable roundRect(int fillColor, int borderColor, int radiusDp, int borderDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (borderDp > 0) {
            drawable.setStroke(dp(borderDp), borderColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchHeight(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void scheduleAutoRefresh(long delayMs) {
        handler.removeCallbacks(autoRefreshRunnable);
        handler.postDelayed(autoRefreshRunnable, delayMs);
    }

    private String currentTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void scheduleSecondTick() {
        long now = SystemClock.uptimeMillis();
        handler.postAtTime(secondTickRunnable, now + (1000L - (now % 1000L)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetRefreshScheduler.cancel(this);
        updatePeakCard();
        handler.removeCallbacks(secondTickRunnable);
        scheduleSecondTick();
        String apiKey = keyInput == null ? "" : keyInput.getText().toString().trim();
        if (!loading && !TextUtils.isEmpty(apiKey)) {
            queryBalance(true);
        } else {
            scheduleAutoRefresh(refreshIntervalMs);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(autoRefreshRunnable);
        handler.removeCallbacks(secondTickRunnable);
        WidgetRefreshScheduler.schedule(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
