package com.erji.geq;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** 竖直推子: Canvas 自绘轨道/填充/滑块, 拖动期间接管父 ScrollView 的拦截。 */
public class VerticalSlider extends View {

    public interface Listener {
        void onValueChanged(float v, boolean dragging);
    }

    private final float min, max;
    private float value;
    private Listener listener;

    private final Paint pTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pZero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float trackW, thumbR;
    private boolean dragging;

    public VerticalSlider(Context c, float min, float max) {
        super(c);
        this.min = min;
        this.max = max;
        float d = c.getResources().getDisplayMetrics().density;
        trackW = 6 * d;
        thumbR = 11 * d;
        pTrack.setColor(0xFF2A3140);
        pFill.setColor(0xE614B8A6);
        pThumb.setColor(0xFF5EEAD4);
        pZero.setColor(0x50FFFFFF);
        pZero.setStrokeWidth(1.5f * d);
        setOnTouchListener((v, ev) -> onTouch(ev));
    }

    public void setValue(float v) {
        value = clamp(v);
        invalidate();
    }

    public void setListener(Listener l) { this.listener = l; }

    private float clamp(float v) { return Math.max(min, Math.min(max, v)); }

    private float padTop() { return thumbR + 6 * getResources().getDisplayMetrics().density; }
    private float padBot() { return thumbR + 6 * getResources().getDisplayMetrics().density; }

    private float valToY(float v) {
        float top = padTop(), bot = getHeight() - padBot();
        return top + (max - v) / (max - min) * (bot - top);
    }

    private float yToVal(float y) {
        float top = padTop(), bot = getHeight() - padBot();
        return max - (y - top) / (bot - top) * (max - min);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float top = padTop(), bot = getHeight() - padBot();
        canvas.drawRoundRect(cx - trackW / 2, top, cx + trackW / 2, bot, trackW / 2, trackW / 2, pTrack);

        float y = valToY(value), y0 = valToY(0);
        float fy0 = Math.min(y, y0), fy1 = Math.max(y, y0);
        canvas.drawRoundRect(cx - trackW / 2, fy0, cx + trackW / 2, fy1, trackW / 2, trackW / 2, pFill);
        canvas.drawLine(cx - trackW, y0, cx + trackW, y0, pZero);
        canvas.drawCircle(cx, y, thumbR, pThumb);
    }

    private boolean onTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                apply(ev.getY(), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) apply(ev.getY(), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    if (getParent() != null)
                        getParent().requestDisallowInterceptTouchEvent(false);
                    if (listener != null) listener.onValueChanged(value, false); // 结束, 触发持久化
                }
                return true;
        }
        return false;
    }

    private void apply(float y, boolean fromUser) {
        float raw = yToVal(y);
        value = clamp(Math.round(raw * 2f) / 2f); // 0.5 dB 步进
        invalidate();
        if (listener != null) listener.onValueChanged(value, true);
    }
}
