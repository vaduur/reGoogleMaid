// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
public final class PairReplyReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) return;
        CharSequence code = results.getCharSequence("code");
        if (code == null || !code.toString().matches("[0-9]{6}")) return;
        context.startForegroundService(new Intent(context,DiscoverService.class).setAction("pair")
            .putExtra("port",intent.getIntExtra("port",0)).putExtra("code",code.toString()));
    }
}
