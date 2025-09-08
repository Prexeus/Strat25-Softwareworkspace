package com.example.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class InputMessage {

    private InputType type;
    private final Map<String, String> kv = new LinkedHashMap<>();

    public InputMessage(InputType type) {
        this.type = type;
        put("type", type.name());
    }

    public InputType getType() { return type; }

    public InputMessage put(String key, Object value) {
        kv.put(key, value == null ? "" : String.valueOf(value));
        return this;
    }

    public String get(String key) {
        return kv.get(key);
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(kv.get(key)); } catch (Exception e) { return def; }
    }

    public double getDouble(String key, double def) {
        try { return Double.parseDouble(kv.get(key)); } catch (Exception e) { return def; }
    }

    // --- Encoding: key=value&key2=value2\n (URL-Encoded)
    public String encodeLine() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(url(e.getKey())).append('=').append(url(e.getValue()));
        }
        return sb.append('\n').toString();
    }

    public static InputMessage decodeLine(String line) throws Exception {
        Map<String,String> map = new LinkedHashMap<>();
        String[] parts = line.trim().split("&");
        for (String p : parts) {
            int idx = p.indexOf('=');
            if (idx <= 0) continue;
            String k = unurl(p.substring(0, idx));
            String v = unurl(p.substring(idx + 1));
            map.put(k, v);
        }
        String t = map.get("type");
        if (t == null) throw new IllegalArgumentException("No type");
        InputType type = InputType.valueOf(t);
        InputMessage m = new InputMessage(type);
        m.kv.clear();
        m.kv.putAll(map);
        return m;
    }

    private static String url(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }
    private static String unurl(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }
}
