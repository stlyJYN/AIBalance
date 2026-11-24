package com.deepseek.balance;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Performs a provider's balance query (and optional today-token query) over
 * plain HttpURLConnection so the project keeps its zero-dependency constraint.
 */
final class ProviderApi {
    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");

    private ProviderApi() {
    }

    static final class ApiException extends Exception {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    static final class BalanceResult {
        final String rawBalance;
        final long tokens;

        BalanceResult(String rawBalance, long tokens) {
            this.rawBalance = rawBalance;
            this.tokens = tokens;
        }
    }

    static BalanceResult query(ProviderConfig config, String apiKey) throws Exception {
        String today = todayString();
        String url = config.balanceUrl
                .replace("{today}", today)
                .replace("{key}", apiKey);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BalanceTool/2.0 Android");
            applyAuth(connection, config, apiKey);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readText(stream);

            if (code != 200) {
                throw new ApiException(code, describeStatus(code));
            }

            JSONObject root = new JSONObject(body);
            String balance = JsonPath.evalString(root, config.balancePath, "");
            if (balance.isEmpty()) {
                throw new Exception("响应中未找到余额字段");
            }

            long tokens = -1L;
            if (!config.usageUrl.isEmpty() && !config.tokensPath.isEmpty()) {
                tokens = fetchTokens(config, apiKey, today);
            }
            return new BalanceResult(balance, tokens);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static long fetchTokens(ProviderConfig config, String apiKey, String today) {
        HttpURLConnection connection = null;
        try {
            String url = config.usageUrl
                    .replace("{today}", today)
                    .replace("{key}", apiKey);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            applyAuth(connection, config, apiKey);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) {
                return -1L;
            }
            JSONObject root = new JSONObject(readText(connection.getInputStream()));
            Object value = JsonPath.eval(root, config.tokensPath);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong(((String) value).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return -1L;
    }

    private static void applyAuth(HttpURLConnection connection, ProviderConfig config, String apiKey) {
        if (!config.authHeaderName.isEmpty()) {
            String value = config.authValueTemplate.replace("{key}", apiKey == null ? "" : apiKey);
            connection.setRequestProperty(config.authHeaderName, value);
        }
    }

    private static String describeStatus(int code) {
        switch (code) {
            case 401:
                return "API Key 无效或已失效";
            case 403:
                return "当前密钥没有查询权限";
            case 429:
                return "请求过于频繁，请稍后再试";
            default:
                return "请求失败（HTTP " + code + "）";
        }
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (InputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String todayString() {
        Calendar calendar = Calendar.getInstance(BEIJING);
        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}
