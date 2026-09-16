package com.erji.geq;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 前台服务: 托管常驻通知, 保活让全局音效不被系统回收; 通知里可直接停用。 */
public class EqService extends Service {

    static final String ACTION_RUN = "com.erji.geq.RUN";
    static final String ACTION_DISABLE = "com.erji.geq.DISABLE";
    private static final String CH = "eq";
    private static final int ID = 1;

    private EqEngine engine;
    private final Runnable onEngineChange = this::updateNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "全局均衡器",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        engine = EqEngine.get(this);
        engine.addListener(onEngineChange);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_RUN;
        if (ACTION_DISABLE.equals(action)) {
            engine.setEnabled(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(ID, buildNotification());
        engine.setEnabled(true);
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent off = new Intent(this, EqService.class).setAction(ACTION_DISABLE);
        PendingIntent pOff = PendingIntent.getService(this, 1, off,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = (engine.isReady() ? engine.bands + " 段" : "不可用")
                + (engine.getPreset() != null ? " · " + engine.getPreset() : "");
        return new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("全局均衡器运行中")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pOpen)
                .addAction(new Notification.Action(android.R.drawable.ic_menu_close_clear_cancel,
                        "停用", pOff))
                .build();
    }

    private void updateNotification() {
        if (engine.isEnabled() && engine.isReady()) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        engine.removeListener(onEngineChange);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
