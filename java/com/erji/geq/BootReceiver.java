package com.erji.geq;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自恢复: 上次退出时是开启状态则重新拉起前台服务挂上均衡器。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!EqEngine.get(context).isEnabled()) return;
        Intent i = new Intent(context, EqService.class).setAction(EqService.ACTION_RUN);
        context.getApplicationContext().startForegroundService(i);
    }
}
