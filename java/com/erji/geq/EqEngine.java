package com.erji.geq;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局均衡器引擎: 持有挂在 audio session 0 (全局输出混合) 上的 DynamicsProcessing 实例。
 * Activity 与前台 Service 都通过单例访问; 状态(开关/各段增益/前级/预设名)持久化在 SharedPreferences。
 */
public class EqEngine {

    private static EqEngine I;
    public static synchronized EqEngine get(Context c) {
        if (I == null) I = new EqEngine(c.getApplicationContext());
        return I;
    }

    public static final float MIN_DB = -12f, MAX_DB = 12f;

    private final Context ctx;
    private final SharedPreferences p;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();

    private DynamicsProcessing dp;
    public int bands;
    public int[] centers;
    public float[] gains;
    private float preamp;
    private boolean enabled;
    private String preset;
    private String lastError;

    /** 预设曲线锚点 {Hz, dB}, 对实际频段中心做对数域线性插值 */
    private static final Map<String, float[][]> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("平坦",     new float[][]{{20, 0}, {20000, 0}});
        PRESETS.put("压低音",   new float[][]{{20, -6}, {125, -6}, {250, -5}, {500, -2}, {1000, 0}, {20000, 0}});
        PRESETS.put("低音增强", new float[][]{{20, 5}, {100, 4}, {250, 2}, {500, 0}, {20000, 0}});
        PRESETS.put("人声清晰", new float[][]{{20, -4}, {150, -3}, {400, -1}, {1000, 1}, {3000, 2}, {6000, 1}, {20000, 0}});
        PRESETS.put("高音增强", new float[][]{{20, 0}, {1000, 0}, {4000, 2}, {8000, 4}, {16000, 5}});
        PRESETS.put("夜间小声", new float[][]{{20, 4}, {125, 3}, {500, 1}, {1000, 0}, {3000, 1}, {8000, 3}, {16000, 4}});
        PRESETS.put("播客人声", new float[][]{{20, -10}, {100, -8}, {300, -3}, {1000, 1}, {3000, 2}, {8000, 0}, {20000, 0}});
    }

    private EqEngine(Context c) {
        ctx = c;
        p = c.getSharedPreferences("geq", Context.MODE_PRIVATE);
        preamp = p.getFloat("preamp", 0f);
        enabled = p.getBoolean("on", false);
        preset = p.getString("preset", "平坦");
        bands = p.getInt("bands", 0);
        if (bands > 0) {
            centers = new int[bands];
            for (int i = 0; i < bands; i++) centers[i] = centerFor(bands, i);
            gains = new float[bands];
            String s = p.getString("gains", null);
            if (s != null) {
                String[] a = s.split(",");
                for (int i = 0; i < bands && i < a.length; i++)
                    try { gains[i] = Float.parseFloat(a[i]); } catch (Exception ignored) {}
            }
        }
    }

    // ---------- 状态查询 ----------

    public boolean isEnabled() { return enabled; }
    public boolean isReady() { return dp != null; }
    public String getPreset() { return preset; }
    public String getLastError() { return lastError; }
    public float getPreamp() { return preamp; }
    public float getGain(int band) { return gains != null ? gains[band] : 0f; }

    public static String[] presetNames() { return PRESETS.keySet().toArray(new String[0]); }

    public static int centerFor(int n, int i) {
        double lo = Math.log(31.25), hi = Math.log(16000);
        return (int) Math.round(Math.exp(lo + (hi - lo) * i / (n - 1)));
    }

    // ---------- 效果生命周期 ----------

    /** 创建 DynamicsProcessing (幂等)。失败返回 false, 原因在 getLastError()。 */
    public synchronized boolean ensureEffect() {
        if (dp != null) return true;
        lastError = null;
        int[] tries = {10, 6, 5, 8, 12};
        for (int n : tries) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,              // 立体声
                        true, n,        // 前置 EQ
                        false, 0,       // 后置 EQ
                        false, 0,       // 多段压限
                        true)           // 限幅器 (防削波)
                        .build();
                DynamicsProcessing d = new DynamicsProcessing(0, 0, cfg); // session 0 = 全局输出
                centers = new int[n];
                gains = new float[n];
                String s = p.getString("gains", null);
                String[] saved = s == null ? new String[0] : s.split(",");
                for (int i = 0; i < n; i++) {
                    centers[i] = centerFor(n, i);
                    float g = 0f;
                    if (i < saved.length)
                        try { g = Float.parseFloat(saved[i]); } catch (Exception ignored) {}
                    gains[i] = g;
                    d.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(true, centers[i], g));
                }
                d.setInputGainAllChannelsTo(preamp);
                bands = n;
                dp = d;
                p.edit().putInt("bands", n).apply();
                if (enabled) {
                    try { dp.setEnabled(true); } catch (Throwable t) { lastError = "" + t; }
                }
                return true;
            } catch (Throwable t) {
                lastError = t.toString();
            }
        }
        return false;
    }

    public synchronized void setEnabled(boolean on) {
        if (on && !ensureEffect()) {
            enabled = false;
        } else {
            enabled = on;
            if (dp != null) {
                try { dp.setEnabled(on); } catch (Throwable t) { lastError = "" + t; }
            }
        }
        p.edit().putBoolean("on", enabled).apply();
        notifyListeners();
    }

    // ---------- 调节 ----------

    public synchronized void setBandGain(int band, float db, boolean persist) {
        if (gains == null || band < 0 || band >= gains.length) return;
        db = Math.max(MIN_DB, Math.min(MAX_DB, db));
        gains[band] = db;
        if (dp != null) {
            try {
                dp.setPreEqBandAllChannelsTo(band, new DynamicsProcessing.EqBand(true, centers[band], db));
            } catch (Throwable ignored) {}
        }
        if (persist) {
            preset = "自定义";
            p.edit().putString("preset", preset).putString("gains", joinGains()).apply();
        }
        notifyListeners();
    }

    public synchronized void setPreamp(float db) {
        preamp = Math.max(-6f, Math.min(6f, db));
        if (dp != null) {
            try { dp.setInputGainAllChannelsTo(preamp); } catch (Throwable ignored) {}
        }
        p.edit().putFloat("preamp", preamp).apply();
        notifyListeners();
    }

    public synchronized void applyPreset(String name) {
        float[][] anchors = PRESETS.get(name);
        if (anchors == null || gains == null) return;
        for (int i = 0; i < gains.length; i++) {
            gains[i] = interp(centers[i], anchors);
            if (dp != null) {
                try {
                    dp.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(true, centers[i], gains[i]));
                } catch (Throwable ignored) {}
            }
        }
        preset = name;
        p.edit().putString("preset", name).putString("gains", joinGains()).apply();
        notifyListeners();
    }

    private static float interp(float freq, float[][] anchors) {
        if (freq <= anchors[0][0]) return anchors[0][1];
        if (freq >= anchors[anchors.length - 1][0]) return anchors[anchors.length - 1][1];
        for (int i = 1; i < anchors.length; i++) {
            if (freq <= anchors[i][0]) {
                double f0 = Math.log(anchors[i - 1][0]), f1 = Math.log(anchors[i][0]);
                double f = Math.log(freq);
                float t = (float) ((f - f0) / (f1 - f0));
                return anchors[i - 1][1] + (anchors[i][1] - anchors[i - 1][1]) * t;
            }
        }
        return 0f;
    }

    private String joinGains() {
        StringBuilder sb = new StringBuilder();
        if (gains != null)
            for (float g : gains) sb.append(g).append(",");
        return sb.toString();
    }

    // ---------- 监听 ----------

    /** 监听回调在主线程执行 */
    public void addListener(Runnable r) { synchronized (listeners) { listeners.add(r); } }
    public void removeListener(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    private void notifyListeners() {
        synchronized (listeners) {
            for (final Runnable r : listeners) main.post(r);
        }
    }
}
