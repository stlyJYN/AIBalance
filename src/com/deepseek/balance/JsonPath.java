package com.deepseek.balance;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A deliberately small JSON path evaluator used to describe where a balance or
 * token count lives in a provider response.
 *
 * Supported syntax:
 *   a.b.c                       plain object traversal
 *   a[].b  or  a[0].b           first element / explicit index of an array
 *   a[currency=CNY].b           first array element whose field matches
 *   sum(a.total_tokens)         sum a numeric field across an array
 *   pathA|pathB                 first alternative that resolves to a value
 */
final class JsonPath {
    private JsonPath() {
    }

    static Object eval(JSONObject root, String path) {
        if (root == null || path == null) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] alternatives = trimmed.split("\\|");
        for (String alternative : alternatives) {
            Object value = evalPath(root, alternative.trim());
            if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
                return value;
            }
        }
        return null;
    }

    static String evalString(JSONObject root, String path, String fallback) {
        Object value = eval(root, path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static Object evalPath(Object node, String path) {
        String trimmed = path.trim();
        if (trimmed.startsWith("sum(") && trimmed.endsWith(")")) {
            return sum(node, trimmed.substring(4, trimmed.length() - 1).trim());
        }

        Object current = node;
        String[] segments = trimmed.split("\\.");
        for (String segment : segments) {
            String step = segment.trim();
            if (step.isEmpty()) {
                continue;
            }
            current = step(current, step);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object step(Object node, String segment) {
        String key = segment;
        String bracket = null;
        int open = segment.indexOf('[');
        if (open >= 0) {
            key = segment.substring(0, open);
            int close = segment.indexOf(']', open);
            bracket = close > open ? segment.substring(open + 1, close) : "";
        }

        Object current = node;
        if (!key.isEmpty()) {
            current = field(current, key);
        }
        if (bracket != null && current != null) {
            current = arrayStep(current, bracket);
        }
        return current;
    }

    private static Object field(Object node, String key) {
        if (node instanceof JSONObject) {
            return ((JSONObject) node).opt(key);
        }
        return null;
    }

    private static Object arrayStep(Object node, String bracket) {
        if (!(node instanceof JSONArray)) {
            return null;
        }
        JSONArray array = (JSONArray) node;
        if (bracket.isEmpty()) {
            return array.length() > 0 ? array.opt(0) : null;
        }
        try {
            return array.opt(Integer.parseInt(bracket));
        } catch (NumberFormatException ignored) {
        }

        int equals = bracket.indexOf('=');
        if (equals > 0) {
            String key = bracket.substring(0, equals).trim();
            String expected = bracket.substring(equals + 1).trim();
            for (int index = 0; index < array.length(); index++) {
                Object element = array.opt(index);
                if (element instanceof JSONObject) {
                    Object actual = ((JSONObject) element).opt(key);
                    if (actual != null && expected.equals(String.valueOf(actual))) {
                        return element;
                    }
                }
            }
        }
        return null;
    }

    private static Object sum(Object node, String innerPath) {
        int lastDot = innerPath.lastIndexOf('.');
        String arrayPath = lastDot >= 0 ? innerPath.substring(0, lastDot).trim() : "";
        String fieldName = lastDot >= 0 ? innerPath.substring(lastDot + 1).trim() : innerPath.trim();

        Object arrayNode = arrayPath.isEmpty() ? node : evalPath(node, arrayPath);
        if (!(arrayNode instanceof JSONArray)) {
            return null;
        }

        JSONArray array = (JSONArray) arrayNode;
        double total = 0;
        boolean found = false;
        for (int index = 0; index < array.length(); index++) {
            Object element = array.opt(index);
            Object fieldValue = element instanceof JSONObject ? ((JSONObject) element).opt(fieldName) : null;
            Double numeric = toDouble(fieldValue);
            if (numeric != null) {
                total += numeric;
                found = true;
            }
        }
        return found ? total : null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
