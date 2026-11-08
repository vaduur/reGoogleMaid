// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context.getSharedPreferences("state",0).getBoolean("auto",false))
            context.startForegroundService(new Intent(context, DiscoverService.class).setAction("watch"));
    }
}
