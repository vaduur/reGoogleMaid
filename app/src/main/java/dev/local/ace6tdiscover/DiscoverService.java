// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.nsd.*;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import io.github.muntashirakon.adb.AdbStream;

public final class DiscoverService extends Service {
    private static final String CHANNEL = "native_discover";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private volatile WirelessAdbIdentity connection;
    private volatile int generation;
    private boolean connecting;
    private boolean pairing;
    private int pairPort;
    private int failures;
    private String status = "Ожидание локальной отладки";
    private boolean automatic() { return getSharedPreferences("state",0).getBoolean("auto",false); }
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { main.post(() -> { if (automatic() && connection == null && !connecting && !pairing) discover(false); }); }
    };
    @Override public void onCreate() {
        super.onCreate();
        nsd = getSystemService(NsdManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Восстановление Discover",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(network);
    }
    private Notification.Builder builder() {
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("re:GoogleMaid · Discover").setContentText(status).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);
    }
    private Notification notification() { return builder().build(); }
    private void setStatus(String text) {
        status = text; getSharedPreferences("state",0).edit().putString("status",text).apply();
        getSystemService(NotificationManager.class).notify(1,notification());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        String action = intent == null ? "watch" : intent.getAction();
        if ("pair".equals(action)) { pair(intent.getIntExtra("port",0),intent.getStringExtra("code")); }
        else if ("prepare_pair".equals(action)) {
            cancel(); pairing = true;
            setStatus("Открой «Сопряжение с помощью кода» и введи код в уведомлении re:GoogleMaid"); discover(true);
            int token = generation; main.postDelayed(() -> { if (generation==token && pairing) { pairing=false; stopDiscovery(); setStatus("Время сопряжения истекло"); if (!automatic()) stopSelf(); } },180000);
        } else if ("restore".equals(action) || automatic()) {
            if (!getSharedPreferences("state",0).getBoolean("paired",false)) { setStatus("Сначала выполни сопряжение"); stopSelf(); }
            else { cancel(); failures=0; pairing=false; discover(false); }
        } else stopSelf();
        return automatic() ? START_STICKY : START_NOT_STICKY;
    }
    private void cancel() {
        generation++; stopDiscovery(); connecting=false;
        WirelessAdbIdentity old=connection; connection=null;
        if (old!=null) new Thread(() -> { try { old.close(); } catch(Exception ignored){} },"close-local-adb").start();
    }
    private void discover(boolean forPair) {
        if (discovery != null || connecting) return;
        if (Settings.Global.getInt(getContentResolver(),"adb_wifi_enabled",0)!=1) {
            setStatus("Нужна включённая беспроводная отладка");
            if (automatic()) main.postDelayed(() -> discover(false),15000);
            return;
        }
        final int token=generation;
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onDiscoveryStopped(String type) {}
            public void onStopDiscoveryFailed(String type,int error) {}
            public void onStartDiscoveryFailed(String type,int error) {
                main.post(() -> { if(discovery==this){ discovery=null; setStatus("Локальная отладка пока недоступна"); if(automatic())main.postDelayed(() -> discover(false),15000); } });
            }
            public void onServiceLost(NsdServiceInfo info) {}
            public void onServiceFound(NsdServiceInfo service) {
                nsd.resolveService(service,new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo info,int error) {}
                    public void onServiceResolved(NsdServiceInfo info) {
                        main.post(() -> {
                            if(token!=generation || discovery!=listener())return;
                            try {
                                if(info.getHost()==null || (!info.getHost().isLoopbackAddress() && NetworkInterface.getByInetAddress(info.getHost())==null))return;
                                if(forPair) showPairInput(info.getPort());
                                else { stopDiscovery(); connect(info.getPort(),token); }
                            }catch(Exception e){setStatus("Не удалось проверить адрес этого телефона");}
                        });
                    }
                });
            }
            private NsdManager.DiscoveryListener listener(){return this;}
        };
        discovery=listener;
        nsd.discoverServices(forPair?"_adb-tls-pairing._tcp.":"_adb-tls-connect._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);
    }
    private void stopDiscovery() {
        NsdManager.DiscoveryListener old=discovery; discovery=null;
        if(old!=null)try{nsd.stopServiceDiscovery(old);}catch(Exception ignored){}
    }
    private void showPairInput(int port) {
        pairPort=port;
        setStatus("Окно сопряжения найдено — введи шестизначный код здесь");
        PendingIntent reply=PendingIntent.getBroadcast(this,1,new Intent(this,PairReplyReceiver.class).putExtra("port",port),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        RemoteInput input=new RemoteInput.Builder("code").setLabel("Шестизначный код").build();
        Notification.Action action=new Notification.Action.Builder(null,"Ввести код",reply).addRemoteInput(input).build();
        getSystemService(NotificationManager.class).notify(1,builder().addAction(action).build());
    }
    private void pair(int port,String code) {
        if(!pairing || pairPort!=port || code==null || !code.matches("[0-9]{6}")){setStatus("Открой новое окно сопряжения");return;}
        cancel(); pairing=false; final int token=generation;
        setStatus("Сопряжение с этим телефоном…");
        io.execute(() -> {
            boolean ok=false;
            try(WirelessAdbIdentity identity=new WirelessAdbIdentity(this)){ok=identity.pair("127.0.0.1",port,code);}
            catch(Exception ignored){}
            final boolean result=ok;
            main.post(() -> {
                if(token!=generation)return;
                if(result)getSharedPreferences("state",0).edit().putBoolean("paired",true).apply();
                setStatus(result?"Сопряжение выполнено. Можно восстановить Discover.":"Сопряжение не выполнено. Попробуй новый код.");
                if(automatic() && result)discover(false);else stopSelf();
            });
        });
    }
    private void connect(int port,int token) {
        if(connecting || token!=generation)return;
        connecting=true; setStatus("Подключение к локальной отладке…");
        io.execute(() -> {
            try(WirelessAdbIdentity identity=new WirelessAdbIdentity(this)){
                connection=identity;
                if(!identity.connect("127.0.0.1",port))throw new IOException("Local ADB rejected");
                String previous="";
                while(token==generation){
                    String pid=shell(identity,"pidof com.android.launcher").trim();
                    if(!pid.isEmpty() && !pid.equals(previous)){
                        if(automatic()){
                            String ready=shell(identity,"if dumpsys window policy | grep -q 'showing=true'; then echo WAIT_LOCKED; elif dumpsys activity activities | grep topResumedActivity= | grep -q 'com.android.launcher/'; then echo HOME_READY; else echo WAIT_HOME; fi");
                            if(!ready.contains("HOME_READY")){
                                main.post(() -> setStatus("Ожидание разблокированного рабочего стола"));
                                Thread.sleep(15000); continue;
                            }
                        }
                        main.post(() -> setStatus("Проверка встроенной панели…"));
                        String script=new String(getAssets().open("bootstrap.sh").readAllBytes(),StandardCharsets.UTF_8);
                        String result=shell(identity,"sh -c "+quote(script));
                        if(!result.contains("NATIVE_READY") && !result.contains("NATIVE_ALREADY_ACTIVE"))
                            throw new IOException("Bootstrap failed: "+result.substring(Math.max(0,result.length()-450)));
                        // Refresh ordinary feature records after the original provider is back.
                        try{getContentResolver().notifyChange(Uri.parse("content://com.oplus.customize.coreapp.configmanager.configprovider.AppFeatureProvider/app_feature"),null,0);}catch(SecurityException ignored){}
                        previous=shell(identity,"pidof com.android.launcher").trim(); failures=0;
                        if(result.contains("NATIVE_READY"))getSharedPreferences("state",0).edit().putInt("restores",getSharedPreferences("state",0).getInt("restores",0)+1).apply();
                        main.post(() -> setStatus("Встроенный Discover подключён. AppPlatform возвращён в исходное состояние."));
                    }
                    if(!automatic())break;
                    Thread.sleep(5000);
                }
                main.post(() -> { if(token==generation && !automatic())stopSelf(); });
            }catch(Exception error){
                main.post(() -> {
                    if(token!=generation)return;
                    setStatus("Восстановление прервано: "+error.getClass().getSimpleName()+" "+String.valueOf(error.getMessage()));
                    if(automatic() && ++failures<=3)main.postDelayed(() -> discover(false),60000);else stopSelf();
                });
            }finally{connection=null;main.post(() -> connecting=false);}
        });
    }
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private static String shell(WirelessAdbIdentity identity,String command)throws Exception{
        try(AdbStream stream=identity.openStream("shell:"+command);ByteArrayOutputStream data=new ByteArrayOutputStream()){
            byte[] buffer=new byte[2048];int count;
            while((count=stream.read(buffer,0,buffer.length))>=0){if(data.size()+count>16384)throw new IOException("Unexpected command output");data.write(buffer,0,count);}
            return data.toString(StandardCharsets.UTF_8);
        }
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        cancel(); main.removeCallbacksAndMessages(null);
        try{getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);}catch(Exception ignored){}
        io.shutdownNow(); super.onDestroy();
    }
}
