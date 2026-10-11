package com.meebook.inkhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Smart Life (Tuya Cloud OpenAPI) and Dreo (Dreo Open API) devices, merged into one list.
 * Ported from the Amazfit "Home Control" app (amazfit-mods/home-control/app-side): same
 * endpoints, signing and commands. Blocking calls: run them off the UI thread.
 *
 * Credentials sit in the app's private files dir, never on /sdcard (Syncthing, other apps):
 *   tuya.json {"accessId", "accessSecret", "region"}   dreo.json {"email", "passwordMd5"}
 */
final class HomeCloud {

    static final class Device {
        String id, source, name, kind;
        boolean online, on;
        // plug / light
        String switchCode;
        Double watts;
        // fan
        int speed, speedMin, speedMax;
        String mode;
        List<String> modes = new ArrayList<>();
        Object osc;
        List<Object> oscOptions = new ArrayList<>();
        Map<String, String> oscLabels = new HashMap<>();
        // light (mode above is "white" or "colour")
        int bright, temp;
        int hue, sat = 1000, val = 1000;

        String oscLabel(Object o) {
            String l = oscLabels.get(String.valueOf(o));
            return l != null ? l : String.valueOf(o);
        }
    }

    static final class Listing {
        final List<Device> devices;
        final List<String> warnings;

        Listing(List<Device> devices, List<String> warnings) {
            this.devices = devices;
            this.warnings = warnings;
        }
    }

    private final File dir;
    private List<Device> devices = new ArrayList<>();

    HomeCloud(File privateDir) {
        dir = privateDir;
    }

    boolean configured() {
        return tuyaConfig() != null || dreoConfig() != null;
    }

    synchronized List<Device> cached() {
        return new ArrayList<>(devices);
    }

    synchronized Device find(String id) {
        for (Device d : devices) if (d.id.equals(id)) return d;
        return null;
    }

    // ------------------------------------------------------------------ hub

    private static final List<String> KIND_ORDER = java.util.Arrays.asList("fan", "light", "plug", "other");

    synchronized Listing list() throws IOException {
        JSONObject tc = tuyaConfig(), dc = dreoConfig();
        if (tc == null && dc == null) {
            throw new IOException("No Smart Life or Dreo login on this tablet yet.");
        }
        List<Device> next = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        IOException offline = null;
        if (tc != null) {
            try {
                next.addAll(tuyaList(tc));
            } catch (Exception e) {
                warnings.add("Smart Life: " + message(e));
                if (isNetwork(e)) offline = (IOException) e;
            }
        }
        if (dc != null) {
            try {
                next.addAll(dreoList(dc));
            } catch (Exception e) {
                warnings.add("Dreo: " + message(e));
                if (isNetwork(e)) offline = (IOException) e;
            }
        }
        // No network at all: rethrow it as-is so the caller can wait for Wi-Fi and retry
        if (next.isEmpty() && offline != null) throw offline;
        if (next.isEmpty() && !warnings.isEmpty()) throw new IOException(String.join("\n", warnings));
        // stable sort: each kind keeps its cloud order
        Collections.sort(next, (a, b) -> KIND_ORDER.indexOf(a.kind) - KIND_ORDER.indexOf(b.kind));
        devices = next;
        return new Listing(cached(), warnings);
    }

    /** Runs one action (power, speed, mode, osc, brightness, white, colour) and updates the device. */
    synchronized void act(Device d, String action, Object value) throws IOException {
        try {
            if ("fan".equals(d.kind)) fanAction(d, action, value);
            else if ("light".equals(d.kind)) lightAction(d, action, value);
            else if ("power".equals(action) && d.switchCode != null) {
                boolean on = (Boolean) value;
                tuyaCommands(d.id, cmd(d.switchCode, on));
                d.on = on;
            } else {
                throw new IOException(d.name + " can't do " + action);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** Turns off everything that is on; returns {turned off, failed}. */
    synchronized int[] allOff() throws IOException {
        list();
        int ok = 0, failed = 0;
        for (Device d : devices) {
            if (!d.online || !d.on || "other".equals(d.kind)) continue;
            try {
                act(d, "power", false);
                ok++;
            } catch (IOException e) {
                failed++;
            }
        }
        return new int[] {ok, failed};
    }

    private void fanAction(Device d, String action, Object value) throws IOException, JSONException {
        JSONObject desired = new JSONObject();
        switch (action) {
            case "power":
                desired.put("poweron", (boolean) (Boolean) value);
                dreoControl(d.id, desired);
                d.on = (Boolean) value;
                return;
            case "speed":
                int speed = Math.min(d.speedMax, Math.max(d.speedMin, (Integer) value));
                desired.put("speed", speed);
                dreoControl(d.id, desired);
                d.speed = speed;
                return;
            case "mode":
                desired.put("mode", value);
                dreoControl(d.id, desired);
                d.mode = (String) value;
                return;
            case "osc":
                desired.put("oscmode", value);
                dreoControl(d.id, desired);
                d.osc = value;
                return;
        }
        throw new IOException("Fan can't do " + action);
    }

    private void lightAction(Device d, String action, Object value) throws IOException, JSONException {
        JSONObject on = cmd("switch_led", true);
        switch (action) {
            case "power":
                tuyaCommands(d.id, cmd("switch_led", (Boolean) value));
                d.on = (Boolean) value;
                return;
            case "brightness": {
                int level = Math.max(10, (Integer) value * 10);
                if ("colour".equals(d.mode)) {
                    tuyaCommands(d.id, on, cmd("colour_data_v2", hsv(d.hue, d.sat, level)));
                    d.val = level;
                } else {
                    tuyaCommands(d.id, on, cmd("bright_value_v2", level));
                    d.bright = level;
                }
                d.on = true;
                return;
            }
            case "white":
                tuyaCommands(d.id, on, cmd("work_mode", "white"), cmd("temp_value_v2", value));
                d.on = true;
                d.mode = "white";
                d.temp = (Integer) value;
                return;
            case "colour": {
                int v = "colour".equals(d.mode) ? d.val : 1000;
                int h = (Integer) value;
                tuyaCommands(d.id, on, cmd("work_mode", "colour"), cmd("colour_data_v2", hsv(h, 1000, v)));
                d.on = true;
                d.mode = "colour";
                d.hue = h;
                d.sat = 1000;
                d.val = v;
                return;
            }
        }
        throw new IOException("Light can't do " + action);
    }

    private static JSONObject cmd(String code, Object value) throws JSONException {
        return new JSONObject().put("code", code).put("value", value);
    }

    private static JSONObject hsv(int h, int s, int v) throws JSONException {
        return new JSONObject().put("h", h).put("s", s).put("v", v);
    }

    // ----------------------------------------------------------------- Tuya

    private static final Map<String, String> TUYA_HOSTS = new HashMap<>();
    static {
        TUYA_HOSTS.put("eu", "openapi.tuyaeu.com");
        TUYA_HOSTS.put("weu", "openapi-weaz.tuyaeu.com");
        TUYA_HOSTS.put("us", "openapi.tuyaus.com");
        TUYA_HOSTS.put("ueaz", "openapi-ueaz.tuyaus.com");
        TUYA_HOSTS.put("in", "openapi.tuyain.com");
        TUYA_HOSTS.put("cn", "openapi.tuyacn.com");
    }

    private String tuyaToken = "";
    private long tuyaExpires;

    private JSONObject tuyaConfig() {
        JSONObject c = readJson("tuya.json");
        return c != null && !c.optString("accessId").isEmpty() && !c.optString("accessSecret").isEmpty() ? c : null;
    }

    private JSONObject tuyaSend(String method, String path, String body, boolean withToken) throws IOException {
        JSONObject c = tuyaConfig();
        if (c == null) throw new IOException("No Smart Life login");
        String id = c.optString("accessId"), secret = c.optString("accessSecret");
        String host = TUYA_HOSTS.get(c.optString("region", "eu").trim().toLowerCase());
        if (host == null) host = TUYA_HOSTS.get("eu");
        String t = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String token = withToken ? tuyaToken : "";
        String stringToSign = method + "\n" + sha256Hex(body) + "\n\n" + path;
        String sign = hmacSha256Hex(secret, id + token + t + nonce + stringToSign).toUpperCase();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("client_id", id);
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("nonce", nonce);
        headers.put("sign_method", "HMAC-SHA256");
        if (!token.isEmpty()) headers.put("access_token", token);
        return parse(http(method, "https://" + host + path, headers, body).body);
    }

    private void tuyaEnsureToken() throws IOException {
        if (!tuyaToken.isEmpty() && System.currentTimeMillis() < tuyaExpires) return;
        tuyaToken = "";
        JSONObject r = tuyaSend("GET", "/v1.0/token?grant_type=1", "", false);
        if (!r.optBoolean("success")) throw new IOException("Login failed: " + r.optString("msg", r.optString("code")));
        JSONObject res = r.optJSONObject("result");
        tuyaToken = res.optString("access_token");
        tuyaExpires = System.currentTimeMillis() + (res.optLong("expire_time") - 60) * 1000;
    }

    private Object tuyaApi(String method, String path, String body) throws IOException {
        tuyaEnsureToken();
        JSONObject r = tuyaSend(method, path, body, true);
        int code = r.optInt("code");
        if (!r.optBoolean("success") && (code == 1010 || code == 1011)) {
            tuyaToken = "";
            tuyaEnsureToken();
            r = tuyaSend(method, path, body, true);
        }
        if (!r.optBoolean("success")) throw new IOException(r.optString("msg", "Tuya error " + r.optInt("code")));
        return r.opt("result");
    }

    private List<Device> tuyaList(JSONObject c) throws IOException {
        JSONArray list = (JSONArray) tuyaApi("GET", "/v2.0/cloud/thing/device?page_size=20", "");
        List<Device> out = new ArrayList<>();
        if (list == null || list.length() == 0) return out;
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < list.length(); i++) {
            if (i > 0) ids.append(',');
            ids.append(list.optJSONObject(i).optString("id"));
        }
        JSONArray statuses = (JSONArray) tuyaApi("GET", "/v1.0/iot-03/devices/status?device_ids=" + ids, "");
        Map<String, JSONArray> byId = new HashMap<>();
        if (statuses != null) {
            for (int i = 0; i < statuses.length(); i++) {
                JSONObject s = statuses.optJSONObject(i);
                byId.put(s.optString("id"), s.optJSONArray("status"));
            }
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.optJSONObject(i);
            out.add(tuyaSummary(d, byId.get(d.optString("id"))));
        }
        return out;
    }

    private static Device tuyaSummary(JSONObject d, JSONArray status) {
        JSONObject s = new JSONObject();
        if (status != null) {
            for (int i = 0; i < status.length(); i++) {
                JSONObject e = status.optJSONObject(i);
                try {
                    s.put(e.optString("code"), e.opt("value"));
                } catch (JSONException ignored) {
                }
            }
        }
        Device x = new Device();
        x.id = d.optString("id");
        x.source = "tuya";
        String name = d.optString("customName", "");
        x.name = (name.isEmpty() ? d.optString("name", "") : name).trim();
        x.online = d.optBoolean("isOnline");

        if (s.has("switch_led")) {
            x.kind = "light";
            x.switchCode = "switch_led";
            x.on = s.optBoolean("switch_led");
            x.mode = s.optString("work_mode", "white");
            x.bright = s.optInt("bright_value_v2", 1000);
            x.temp = s.optInt("temp_value_v2", 500);
            Object colour = s.opt("colour_data_v2");
            JSONObject hsv = null;
            try {
                hsv = colour instanceof JSONObject ? (JSONObject) colour
                        : colour instanceof String ? new JSONObject((String) colour) : null;
            } catch (JSONException ignored) {
            }
            if (hsv != null) {
                x.hue = hsv.optInt("h", 0);
                x.sat = hsv.optInt("s", 1000);
                x.val = hsv.optInt("v", 1000);
            }
            return x;
        }
        for (String code : new String[] {"switch_1", "switch"}) {
            if (s.has(code)) {
                x.kind = "plug";
                x.switchCode = code;
                x.on = s.optBoolean(code);
                Object p = s.opt("cur_power");
                x.watts = p instanceof Number ? ((Number) p).doubleValue() / 10 : null;
                return x;
            }
        }
        x.kind = "other";
        return x;
    }

    private void tuyaCommands(String id, JSONObject... commands) throws IOException, JSONException {
        JSONArray list = new JSONArray();
        for (JSONObject c : commands) list.put(c);
        tuyaApi("POST", "/v1.0/iot-03/devices/" + id + "/commands",
                new JSONObject().put("commands", list).toString());
    }

    // ----------------------------------------------------------------- Dreo

    private static final String DREO_US = "https://open-api-us.dreo-tech.com";
    private static final String DREO_EU = "https://open-api-eu.dreo-tech.com";
    private static final String DREO_CLIENT_ID = "<DREO_CLIENT_ID>";
    private static final String DREO_CLIENT_SECRET = "<DREO_CLIENT_SECRET>";
    private static final String DREO_PREFIX = "dreo:";

    private String dreoToken = "";
    private String dreoHost = DREO_US;

    private JSONObject dreoConfig() {
        JSONObject c = readJson("dreo.json");
        return c != null && !c.optString("email").isEmpty() && !c.optString("passwordMd5").isEmpty() ? c : null;
    }

    private Map<String, String> dreoHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("UA", "openapi/1.0.0");
        if (!dreoToken.isEmpty()) h.put("Authorization", "Bearer " + dreoToken);
        return h;
    }

    private void dreoLogin() throws IOException {
        JSONObject c = dreoConfig();
        if (c == null) throw new IOException("No Dreo login");
        String body;
        try {
            body = new JSONObject()
                    .put("client_id", DREO_CLIENT_ID).put("client_secret", DREO_CLIENT_SECRET)
                    .put("grant_type", "openapi").put("scope", "all")
                    .put("email", c.optString("email").trim()).put("password", c.optString("passwordMd5").trim())
                    .toString();
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        dreoToken = "";
        Response res = http("POST", DREO_US + "/api/oauth/login?timestamp=" + System.currentTimeMillis(),
                dreoHeaders(), body);
        JSONObject r = parse(res.body);
        if (r.optInt("code", -1) != 0) {
            throw new IOException("Dreo login failed: " + r.optString("msg", String.valueOf(res.status)));
        }
        String[] parts = r.optJSONObject("data").optString("access_token").split(":");
        dreoToken = parts[0];
        dreoHost = parts.length > 1 && parts[1].equalsIgnoreCase("EU") ? DREO_EU : DREO_US;
    }

    private Object dreoApi(String method, String path, String body, boolean retry) throws IOException {
        if (dreoToken.isEmpty()) dreoLogin();
        String sep = path.contains("?") ? "&" : "?";
        Response res = http(method, dreoHost + path + sep + "timestamp=" + System.currentTimeMillis(),
                dreoHeaders(), body);
        if (res.status == 401 && retry) {
            dreoToken = "";
            return dreoApi(method, path, body, false);
        }
        JSONObject r = parse(res.body);
        if (r.optInt("code", -1) != 0) throw new IOException("Dreo: " + r.optString("msg", String.valueOf(res.status)));
        return r.opt("data");
    }

    private List<Device> dreoList(JSONObject c) throws IOException {
        Object data = dreoApi("GET", "/api/device/list", "", true);
        JSONArray list = data instanceof JSONArray ? (JSONArray) data : null;
        if (data instanceof JSONObject) {
            JSONObject o = (JSONObject) data;
            list = o.optJSONArray("list");
            if (list == null) list = o.optJSONArray("devices");
        }
        List<Device> out = new ArrayList<>();
        if (list != null) for (int i = 0; i < list.length(); i++) out.add(dreoSummary(list.optJSONObject(i)));
        return out;
    }

    private static Device dreoSummary(JSONObject d) {
        JSONObject s = d.optJSONObject("state");
        JSONObject c = d.optJSONObject("config");
        if (s == null) s = new JSONObject();
        if (c == null) c = new JSONObject();
        Device x = new Device();
        x.id = DREO_PREFIX + d.optString("deviceSn");
        x.source = "dreo";
        String name = d.optString("deviceName", "");
        x.name = (name.isEmpty() ? d.optString("model", "") : name).trim();
        x.online = s.optBoolean("connected");
        x.on = s.optBoolean("poweron");
        if (!d.optString("deviceType", "").toLowerCase().contains("fan")) {
            x.kind = "other";
            return x;
        }
        x.kind = "fan";
        JSONArray range = c.optJSONArray("speed_range");
        x.speedMin = range != null ? range.optInt(0, 1) : 1;
        x.speedMax = range != null ? range.optInt(1, 8) : 8;
        x.speed = s.optInt("speed", 0);
        if (x.speed == 0) x.speed = x.speedMin;
        x.mode = s.optString("mode", "");
        JSONArray modes = c.optJSONArray("preset_modes");
        if (modes != null) for (int i = 0; i < modes.length(); i++) x.modes.add(modes.optString(i));
        x.osc = s.opt("oscmode");
        JSONArray opts = c.optJSONArray("selectOptions");
        if (opts != null) for (int i = 0; i < opts.length(); i++) x.oscOptions.add(opts.opt(i));
        JSONObject labels = c.optJSONObject("selectOptionLabel");
        if (labels != null) {
            java.util.Iterator<String> keys = labels.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                x.oscLabels.put(k, labels.optString(k));
            }
        }
        return x;
    }

    private void dreoControl(String id, JSONObject desired) throws IOException, JSONException {
        dreoApi("POST", "/api/device/control", new JSONObject()
                .put("devicesn", id.substring(DREO_PREFIX.length())).put("desired", desired).toString(), true);
    }

    // ----------------------------------------------------------------- HTTP and helpers

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static Response http(String method, String url, Map<String, String> headers, String body)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod(method);
            c.setRequestProperty("Content-Type", "application/json");
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (body != null && !body.isEmpty()) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(status, in == null ? "" : readAll(in));
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = s.read(b)) > 0; ) buf.write(b, 0, n);
            return buf.toString("UTF-8");
        }
    }

    private static JSONObject parse(String body) throws IOException {
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("Unexpected reply from the cloud");
        }
    }

    private JSONObject readJson(String name) {
        try {
            return new JSONObject(new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isNetwork(Exception e) {
        return e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException;
    }

    static String message(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "No internet. Is Wi-Fi connected?";
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static String sha256Hex(String s) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String hmacSha256Hex(String key, String s) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
