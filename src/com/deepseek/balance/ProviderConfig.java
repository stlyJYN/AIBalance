package com.deepseek.balance;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A queryable service provider. Built-in providers (DeepSeek / MiMo / Zhipu) are
 * created on first launch and can be extended with arbitrary custom providers.
 */
public final class ProviderConfig {
    public static final String PREFS_NAME = "deepseek_balance";
    public static final String PREF_PROVIDERS = "providers";
    public static final String PREF_SELECTED_PROVIDER = "selected_provider";

    public static final String TYPE_DEEPSEEK = "DEEPSEEK";
    public static final String TYPE_MIMO = "MIMO";
    public static final String TYPE_ZHIPU = "ZHIPU";
    public static final String TYPE_CUSTOM = "CUSTOM";

    public final String id;
    public final String name;
    public final String type;
    public final String balanceUrl;
    public final String authHeaderName;
    public final String authValueTemplate;
    public final String balancePath;
    public final String currency;
    public final String symbol;
    public final boolean peakEnabled;
    public final String usageUrl;
    public final String tokensPath;

    public ProviderConfig(
            String id,
            String name,
            String type,
            String balanceUrl,
            String authHeaderName,
            String authValueTemplate,
            String balancePath,
            String currency,
            String symbol,
            boolean peakEnabled,
            String usageUrl,
            String tokensPath) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.balanceUrl = emptyIfNull(balanceUrl);
        this.authHeaderName = emptyIfNull(authHeaderName);
        this.authValueTemplate = emptyIfNull(authValueTemplate);
        this.balancePath = emptyIfNull(balancePath);
        this.currency = emptyIfNull(currency);
        this.symbol = emptyIfNull(symbol);
        this.peakEnabled = peakEnabled;
        this.usageUrl = emptyIfNull(usageUrl);
        this.tokensPath = emptyIfNull(tokensPath);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    public static ProviderConfig deepseek() {
        return new ProviderConfig(
                "deepseek",
                "DeepSeek",
                TYPE_DEEPSEEK,
                "https://api.deepseek.com/user/balance",
                "Authorization",
                "Bearer {key}",
                "balance_infos[currency=CNY].total_balance",
                "CNY",
                "¥",
                true,
                "",
                "");
    }

    public static ProviderConfig mimo() {
        return new ProviderConfig(
                "mimo",
                "小米 MiMo",
                TYPE_MIMO,
                "https://api.xiaomimimo.com/v1/platform/balance",
                "api-key",
                "{key}",
                "balance_infos[currency=CNY].total_available",
                "CNY",
                "¥",
                false,
                "https://api.xiaomimimo.com/v1/platform/usage/daily?start_date={today}&end_date={today}",
                "sum(data.total_tokens)");
    }

    public static ProviderConfig zhipu() {
        return new ProviderConfig(
                "zhipu",
                "智谱 GLM",
                TYPE_ZHIPU,
                "https://open.bigmodel.cn/api/biz/account/query-customer-account-report",
                "Authorization",
                "Bearer {key}",
                "data.availableBalance|data.balance",
                "CNY",
                "¥",
                false,
                "",
                "");
    }

    public static ProviderConfig customTemplate(String id) {
        return new ProviderConfig(
                id,
                "自定义服务商",
                TYPE_CUSTOM,
                "https://example.com/api/balance",
                "Authorization",
                "Bearer {key}",
                "data.balance",
                "CNY",
                "¥",
                false,
                "",
                "");
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("name", name);
            object.put("type", type);
            object.put("balanceUrl", balanceUrl);
            object.put("authHeaderName", authHeaderName);
            object.put("authValueTemplate", authValueTemplate);
            object.put("balancePath", balancePath);
            object.put("currency", currency);
            object.put("symbol", symbol);
            object.put("peakEnabled", peakEnabled);
            object.put("usageUrl", usageUrl);
            object.put("tokensPath", tokensPath);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static ProviderConfig fromJson(JSONObject object) {
        return new ProviderConfig(
                object.optString("id", ""),
                object.optString("name", ""),
                object.optString("type", TYPE_CUSTOM),
                object.optString("balanceUrl", ""),
                object.optString("authHeaderName", ""),
                object.optString("authValueTemplate", ""),
                object.optString("balancePath", ""),
                object.optString("currency", "CNY"),
                object.optString("symbol", "¥"),
                object.optBoolean("peakEnabled", false),
                object.optString("usageUrl", ""),
                object.optString("tokensPath", ""));
    }

    public static String apiKeyPrefKey(String providerId) {
        return "api_key_" + providerId;
    }

    public static List<ProviderConfig> ensureBuiltins(Context context) {
        migrateLegacyKey(context);
        List<ProviderConfig> providers = loadAll(context);
        if (providers.isEmpty()) {
            providers = new ArrayList<>();
            providers.add(deepseek());
            providers.add(mimo());
            providers.add(zhipu());
            saveAll(context, providers);
        }
        return providers;
    }

    private static void migrateLegacyKey(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String legacy = preferences.getString("api_key", "");
        if (!legacy.isEmpty() && preferences.getString(apiKeyPrefKey("deepseek"), "").isEmpty()) {
            preferences.edit().putString(apiKeyPrefKey("deepseek"), legacy).apply();
        }
    }

    public static List<ProviderConfig> loadAll(Context context) {
        List<ProviderConfig> result = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(PREF_PROVIDERS, "");
        if (raw.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object != null) {
                    result.add(fromJson(object));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void saveAll(Context context, List<ProviderConfig> providers) {
        JSONArray array = new JSONArray();
        for (ProviderConfig provider : providers) {
            array.put(provider.toJson());
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_PROVIDERS, array.toString())
                .apply();
    }

    public static ProviderConfig find(List<ProviderConfig> providers, String id) {
        if (id == null) {
            return null;
        }
        for (ProviderConfig provider : providers) {
            if (id.equals(provider.id)) {
                return provider;
            }
        }
        return null;
    }

    public static String getSelectedId(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String selected = preferences.getString(PREF_SELECTED_PROVIDER, "");
        if (selected.isEmpty()) {
            return "deepseek";
        }
        return selected;
    }

    public static void setSelectedId(Context context, String providerId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_SELECTED_PROVIDER, providerId)
                .apply();
    }

    public static String getApiKey(Context context, String providerId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(apiKeyPrefKey(providerId), "").trim();
    }

    public static void saveApiKey(Context context, String providerId, String apiKey) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            editor.remove(apiKeyPrefKey(providerId));
        } else {
            editor.putString(apiKeyPrefKey(providerId), apiKey.trim());
        }
        editor.apply();
    }

    public static void deleteProvider(Context context, String providerId) {
        List<ProviderConfig> providers = loadAll(context);
        ProviderConfig target = null;
        for (ProviderConfig provider : providers) {
            if (provider.id.equals(providerId)) {
                target = provider;
                break;
            }
        }
        if (target == null || !TYPE_CUSTOM.equals(target.type)) {
            return;
        }
        providers.remove(target);
        saveAll(context, providers);
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().remove(apiKeyPrefKey(providerId)).apply();
        if (providerId.equals(getSelectedId(context))) {
            setSelectedId(context, "deepseek");
        }
    }
}
