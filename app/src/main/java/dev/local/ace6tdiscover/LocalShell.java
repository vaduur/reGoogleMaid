// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;

import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import io.github.muntashirakon.adb.AdbStream;

/** Local-device ADB only. Never accepts a host or arbitrary command from an exported component. */
final class LocalShell {
    private final Context context;
    LocalShell(Context c){context=c.getApplicationContext();}
    String run(String script)throws Exception {
        if(!context.getSharedPreferences("state",0).getBoolean("paired",false))throw new IOException("Сначала выполни сопряжение на шаге 1");
        if(Settings.Global.getInt(context.getContentResolver(),"adb_wifi_enabled",0)!=1)throw new IOException("Включи беспроводную отладку на шаге 1");
        int port=findPort();
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        try(WirelessAdbIdentity identity=new WirelessAdbIdentity(context)){
            var timeout=timer.schedule(()->{try{identity.close();}catch(Exception ignored){}},50,TimeUnit.SECONDS);
            if(!identity.connect("127.0.0.1",port))throw new IOException("Телефон не принял подключение. Повтори сопряжение");
            String marker="__REGOOGLE_MAID_EXIT_"+java.util.UUID.randomUUID().toString().replace("-","")+":";
            String wrapped="sh -c "+quote("sh -c "+quote(script)+"\nr=$?; printf '\\n"+marker+"%s\\n' \"$r\"; exit \"$r\"");
            try(AdbStream stream=identity.openStream("shell:"+wrapped);ByteArrayOutputStream data=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=stream.read(buffer,0,buffer.length))>=0){if(data.size()+n>196608)throw new IOException("Ответ команды слишком большой");data.write(buffer,0,n);String partial=data.toString(StandardCharsets.UTF_8);int at=partial.indexOf(marker);if(at>=0&&partial.indexOf('\n',at+marker.length())>=0)break;}
                String text=data.toString(StandardCharsets.UTF_8);int at=text.indexOf(marker);
                if(at<0)throw new IOException("Подключение прервано до завершения команды");
                int end=text.indexOf('\n',at+marker.length());if(end<0)throw new IOException("Не получен полный результат команды");
                String code=text.substring(at+marker.length(),end).trim();
                String output=text.substring(0,at).trim();
                if(!"0".equals(code))throw new IOException("Не все действия выполнены ("+code+"). Часть настроек могла примениться.\n"+output.substring(Math.max(0,output.length()-1800)));
                timeout.cancel(false);return output;
            }
        }finally{timer.shutdownNow();}
    }
    private int findPort()throws Exception {
        NsdManager nsd=context.getSystemService(NsdManager.class);CompletableFuture<Integer> result=new CompletableFuture<>();Handler ui=new Handler(Looper.getMainLooper());
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String s){}public void onDiscoveryStopped(String s){}
            public void onStopDiscoveryFailed(String s,int e){}
            public void onStartDiscoveryFailed(String s,int e){result.completeExceptionally(new IOException("Локальная отладка недоступна ("+e+")"));}
            public void onServiceLost(NsdServiceInfo i){}
            public void onServiceFound(NsdServiceInfo i){nsd.resolveService(i,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo x,int e){}
                public void onServiceResolved(NsdServiceInfo x){try{if(x.getHost()!=null&&(x.getHost().isLoopbackAddress()||NetworkInterface.getByInetAddress(x.getHost())!=null))result.complete(x.getPort());}catch(Exception ignored){}}
            });}
        };
        ui.post(()->{try{nsd.discoverServices("_adb-tls-connect._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception e){result.completeExceptionally(e);}});
        try{return result.get(18,TimeUnit.SECONDS);}catch(TimeoutException e){throw new IOException("Телефон не найден. Нужны Wi-Fi и беспроводная отладка");}
        finally{ui.post(()->{try{nsd.stopServiceDiscovery(listener);}catch(Exception ignored){}});}
    }
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
}
