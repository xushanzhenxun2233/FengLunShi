package com.fenglunshi.clock;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Lightweight weather & geolocation fetcher designed to work reliably on Android 4.0+ (API 14+).
 * Uses Open-Meteo (with HTTP fallback for legacy Android 4.x TLS limitations) + ip-api.com + LocationManager.
 */
public class WeatherManager {

    public interface Callback {
        void onWeatherUpdated(String displayText);
        void onWeatherError(String message);
    }

    public static class CityPreset {
        public final String name;
        public final double lat;
        public final double lon;

        public CityPreset(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final CityPreset[] PRESET_CITIES = new CityPreset[]{
            new CityPreset("北京", 39.9042, 116.4074),
            new CityPreset("上海", 31.2304, 121.4737),
            new CityPreset("广州", 23.1291, 113.2644),
            new CityPreset("深圳", 22.5431, 114.0579),
            new CityPreset("杭州", 30.2741, 120.1551),
            new CityPreset("成都", 30.5728, 104.0668),
            new CityPreset("武汉", 30.5928, 114.3055),
            new CityPreset("南京", 32.0603, 118.7969),
            new CityPreset("重庆", 29.5630, 106.5516),
            new CityPreset("西安", 34.3416, 108.9398),
            new CityPreset("苏州", 31.2989, 120.5853),
            new CityPreset("天津", 39.3434, 117.3616),
            new CityPreset("长沙", 28.2282, 112.9388),
            new CityPreset("郑州", 34.7466, 113.6253),
            new CityPreset("青岛", 36.0671, 120.3826),
            new CityPreset("哈尔滨", 45.8038, 126.5350)
    };

    private final Context appContext;
    private final ClockPrefs prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WeatherManager(Context context, ClockPrefs prefs) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
    }

    public void fetchWeather(final Callback callback) {
        new Thread(() -> {
            try {
                String city = prefs.getWeatherCity();
                double lat = prefs.getWeatherLat();
                double lon = prefs.getWeatherLon();

                if (prefs.isAutoWeatherLocation()) {
                    double[] ipLoc = resolveByIp();
                    if (ipLoc != null) {
                        lat = ipLoc[0];
                        lon = ipLoc[1];
                        city = prefs.getWeatherCity();
                    } else {
                        Location loc = getLastKnownLocation();
                        if (loc != null) {
                            lat = loc.getLatitude();
                            lon = loc.getLongitude();
                            if (TextUtils.isEmpty(city)) {
                                city = "当地";
                            }
                        }
                    }
                    prefs.setWeatherCoords(lat, lon);
                }

                if (TextUtils.isEmpty(city)) {
                    city = "当地";
                }

                String result = fetchOpenMeteo(city, lat, lon);
                if (result == null) {
                    result = fetchWttrFallback(city, lat, lon);
                }

                if (result != null) {
                    prefs.setCachedWeatherText(result);
                    final String finalResult = result;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onWeatherUpdated(finalResult);
                    });
                } else {
                    postError(callback, "天气获取失败，点击重试");
                }
            } catch (Exception e) {
                postError(callback, "网络不可用，点击重试");
            }
        }).start();
    }

    public void searchAndSelectCity(final String query, final Callback callback) {
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                String body = httpGetWithFallback(
                        "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=zh&format=json",
                        "http://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=zh&format=json"
                );
                if (body != null) {
                    JSONObject root = new JSONObject(body);
                    JSONArray results = root.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        JSONObject item = results.getJSONObject(0);
                        String name = item.optString("name", query.trim());
                        double lat = item.getDouble("latitude");
                        double lon = item.getDouble("longitude");
                        prefs.setAutoWeatherLocation(false);
                        prefs.setWeatherCity(name);
                        prefs.setWeatherCoords(lat, lon);
                        fetchWeather(callback);
                        return;
                    }
                }
                postError(callback, "未找到城市：" + query);
            } catch (Exception e) {
                postError(callback, "搜索城市失败，请检查网络");
            }
        }).start();
    }

    private double[] resolveByIp() {
        try {
            String json = httpGet("http://ip-api.com/json/?lang=zh-CN");
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                if ("success".equalsIgnoreCase(obj.optString("status"))) {
                    double lat = obj.optDouble("lat", Double.NaN);
                    double lon = obj.optDouble("lon", Double.NaN);
                    String city = obj.optString("city", "");
                    if (TextUtils.isEmpty(city)) {
                        city = obj.optString("regionName", "当地");
                    }
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        prefs.setWeatherCity(city);
                        return new double[]{lat, lon};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("MissingPermission")
    private Location getLastKnownLocation() {
        if (!PermissionHelper.hasLocationPermission(appContext)) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            String[] providers = new String[]{
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
            };
            for (String p : providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (best == null || l.getTime() > best.getTime())) {
                            best = l;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return best;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchOpenMeteo(String city, double lat, double lon) {
        String query = String.format(Locale.US,
                "latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                lat, lon);
        String body = httpGetWithFallback(
                "https://api.open-meteo.com/v1/forecast?" + query,
                "http://api.open-meteo.com/v1/forecast?" + query
        );
        if (body == null) return null;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject current = root.optJSONObject("current");
            if (current == null) return null;

            int temp = (int) Math.round(current.optDouble("temperature_2m", 0));
            int humidity = current.optInt("relative_humidity_2m", -1);
            int code = current.optInt("weather_code", 0);
            String desc = mapWmoCode(code);

            String range = "";
            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray mins = daily.optJSONArray("temperature_2m_min");
                JSONArray maxs = daily.optJSONArray("temperature_2m_max");
                if (mins != null && maxs != null && mins.length() > 0 && maxs.length() > 0) {
                    int tMin = (int) Math.round(mins.optDouble(0));
                    int tMax = (int) Math.round(maxs.optDouble(0));
                    range = "  " + tMin + "°/" + tMax + "°";
                }
            }
            String hum = humidity >= 0 ? ("  湿度" + humidity + "%") : "";
            return city + " · " + desc + " " + temp + "°C" + range + hum;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchWttrFallback(String city, double lat, double lon) {
        try {
            String url = String.format(Locale.US, "http://wttr.in/%.4f,%.4f?format=j1&lang=zh", lat, lon);
            String body = httpGet(url);
            if (body == null) return null;
            JSONObject root = new JSONObject(body);
            JSONArray curArr = root.optJSONArray("current_condition");
            if (curArr == null || curArr.length() == 0) return null;
            JSONObject cur = curArr.getJSONObject(0);
            String temp = cur.optString("temp_C", "--");
            String hum = cur.optString("humidity", "");
            String desc = "多云";
            JSONArray zhArr = cur.optJSONArray("lang_zh");
            if (zhArr != null && zhArr.length() > 0) {
                desc = zhArr.getJSONObject(0).optString("value", desc);
            }
            String humPart = TextUtils.isEmpty(hum) ? "" : ("  湿度" + hum + "%");
            return city + " · " + desc + " " + temp + "°C" + humPart;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String httpGetWithFallback(String httpsUrl, String httpUrl) {
        String res = httpGet(httpsUrl);
        if (res != null) return res;
        return httpGet(httpUrl);
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "FengLunShi/1.0 (Android)");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                InputStream is = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private void postError(final Callback callback, final String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onWeatherError(msg);
        });
    }

    private static String mapWmoCode(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "大部晴朗";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code == 61 || code == 80) return "小雨";
        if (code == 63 || code == 81) return "中雨";
        if (code == 65 || code == 82) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code == 85 || code == 86) return "阵雪";
        if (code >= 95) return "雷阵雨";
        return "多云";
    }
}
