package com.typingsushi;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal JSON helpers, sufficient for this app's fixed request/response shapes. */
final class Json {

    private Json() {
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    /**
     * Extracts a string field value from a flat JSON object, e.g. "name":"Bob".
     * The value pattern is written "unrolled" ([^..]* first, then one loop per
     * escape) so long values don't overflow the regex engine's stack.
     */
    static String getString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t");
    }

    /** Extracts a boolean field value from a flat JSON object, e.g. "enabled":true. */
    static Boolean getBoolean(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(json);
        if (!m.find()) return null;
        return Boolean.parseBoolean(m.group(1));
    }

    /** Extracts a numeric field value from a flat JSON object, e.g. "earned":12.5. */
    static Double getNumber(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        if (!m.find()) return null;
        return Double.parseDouble(m.group(1));
    }
}
