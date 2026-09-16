package com.erji.geq;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    EqEngine engine;

    Switch swEnable;
    TextView tvState, tvPre, tvEqError;
    View eqErrorBox;
    LinearLayout bandBox, gutter, presetRow1, presetRow2;
    SeekBar sbPre;

    VerticalSlider[] sliders;
    TextView[] valueLabels;
    final List<Button> chips = new ArrayList<>();
    boolean suppressSwitch, suppressPre;
    final Runnable refresh = this::refreshUi;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = EqEngine.get(this);
        boolean ok = engine.ensureEffect();
        setContentView(R.layout.activity_main);
        // Android 13+ 通知运行时权限: 常驻保活通知需要它 (拒绝不影响音效, 只是没有通知栏入口)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                "android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
        }
        swEnable = findViewById(R.id.swEnable);
        tvState = findViewById(R.id.tvState);
        tvPre = findViewById(R.id.tvPre);
        tvEqError = findViewById(R.id.tvEqError);
        eqErrorBox = findViewById(R.id.eqError);
        bandBox = findViewById(R.id.bandBox);
        gutter = findViewById(R.id.gutter);
        presetRow1 = findViewById(R.id.presetRow1);
        presetRow2 = findViewById(R.id.presetRow2);
        sbPre = findViewById(R.id.sbPre);

        if (!ok) {
            eqErrorBox.setVisibility(View.VISIBLE);
            tvEqError.setText("此系统未能创建全局音效 (DynamicsProcessing):\n" + engine.getLastError());
        }

        swEnable.setOnCheckedChangeListener((btn, on) -> {
            if (suppressSwitch) return;
            if (on) {
                boolean can = engine.ensureEffect();
                if (!can) {
                    eqErrorBox.setVisibility(View.VISIBLE);
                    tvEqError.setText("此系统未能创建全局音效 (DynamicsProcessing):\n" + engine.getLastError());
                    suppressSwitch = true; swEnable.setChecked(false); suppressSwitch = false;
                    refreshUi();
                    return;
                }
                Intent i = new Intent(this, EqService.class).setAction(EqService.ACTION_RUN);
                startForegroundService(i);
                engine.setEnabled(true);
            } else {
                engine.setEnabled(false);
                stopService(new Intent(this, EqService.class));
            }
        });

        sbPre.setMax(12);
        sbPre.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                engine.setPreamp(p - 6);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        buildPresetChips();
        buildBandRows();
        engine.addListener(refresh);
        refreshUi();

        if (engine.isEnabled()) startForegroundService(
                new Intent(this, EqService.class).setAction(EqService.ACTION_RUN));
    }

    @Override
    protected void onDestroy() {
        engine.removeListener(refresh);
        super.onDestroy();
    }

    // ---------------- 预设 ----------------

    void buildPresetChips() {
        String[] names = EqEngine.presetNames();
        int half = (names.length + 1) / 2;
        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            Button chip = new Button(this);
            chip.setText(name);
            chip.setAllCaps(false);
            chip.setTextSize(13);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text));
            chip.setBackgroundResource(R.drawable.chip);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setPadding(dp(10), dp(7), dp(10), dp(7));
            chip.setOnClickListener(v -> engine.applyPreset(name));
            chips.add(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            (i < half ? presetRow1 : presetRow2).addView(chip, lp);
        }
    }

    // ---------------- 频段 ----------------

    void buildBandRows() {
        if (!engine.isReady()) return;
        int n = engine.bands;
        sliders = new VerticalSlider[n];
        valueLabels = new TextView[n];

        // 左侧刻度: +12 / 0 / -12
        gutterLabel("+12", Gravity.TOP);
        gutterLabel("0", Gravity.CENTER);
        gutterLabel("-12", Gravity.BOTTOM);

        for (int i = 0; i < n; i++) {
            final int bi = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView val = new TextView(this);
            val.setTextSize(10);
            val.setTextColor(0xFF8A94A6);
            val.setGravity(Gravity.CENTER);
            col.addView(val, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
            valueLabels[i] = val;

            VerticalSlider slider = new VerticalSlider(this, EqEngine.MIN_DB, EqEngine.MAX_DB);
            slider.setListener((v, dragging) -> {
                valueLabels[bi].setText(fmtDb(v));
                valueLabels[bi].setTextColor(v == 0 ? 0xFF8A94A6 : 0xFF5EEAD4);
                engine.setBandGain(bi, v, !dragging); // 拖动中只应用, 松手才持久化
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            slp.setMargins(dp(2), dp(4), dp(2), dp(4));
            col.addView(slider, slp);
            sliders[i] = slider;

            TextView freq = new TextView(this);
            freq.setTextSize(10);
            freq.setTextColor(0xFF8A94A6);
            freq.setGravity(Gravity.CENTER);
            freq.setText(freqLabel(EqEngine.centerFor(n, i)));
            col.addView(freq, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            bandBox.addView(col, clp);
        }
    }

    void gutterLabel(String s, int gravity) {
        FrameLayout frame = new FrameLayout(this);
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(9);
        t.setTextColor(0xFF5A6474);
        frame.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity | Gravity.CENTER_HORIZONTAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(0, dp(20), 0, dp(18)); // 与推子列的数值/频率标签高度对应
        gutter.addView(frame, lp);
    }

    // ---------------- 状态刷新 ----------------

    void refreshUi() {
        suppressSwitch = true;
        swEnable.setChecked(engine.isEnabled());
        suppressSwitch = false;

        boolean on = engine.isEnabled() && engine.isReady();
        tvState.setText(on
                ? "运行中 · " + engine.bands + " 段 · " + engine.getPreset()
                : (engine.isEnabled() && !engine.isReady() ? "此系统不支持" : "已停用"));

        for (Button c : chips) c.setSelected(nameMatches(c));

        if (engine.isReady() && sliders != null) {
            for (int i = 0; i < sliders.length && i < engine.gains.length; i++) {
                float g = engine.gains[i];
                sliders[i].setValue(g);
                valueLabels[i].setText(fmtDb(g));
                valueLabels[i].setTextColor(g == 0 ? 0xFF8A94A6 : 0xFF5EEAD4);
            }
        }

        suppressPre = true;
        sbPre.setProgress((int) engine.getPreamp() + 6);
        suppressPre = false;
        float pre = engine.getPreamp();
        tvPre.setText(fmtDb(pre) + " dB");
    }

    boolean nameMatches(Button c) {
        return engine.isEnabled() && c.getText().toString().equals(engine.getPreset());
    }

    static String fmtDb(float v) {
        if (v == 0) return "0";
        if (v == Math.floor(v)) return String.format("%+.0f", v);
        return String.format("%+.1f", v);
    }

    static String freqLabel(int hz) {
        return hz >= 1000 ? (hz / 1000) + "k" : String.valueOf(hz);
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
