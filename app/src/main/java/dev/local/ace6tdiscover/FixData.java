// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;

import android.content.Context;
import android.content.pm.*;
import java.util.*;

final class FixData {
    static final String GOOGLE="com.google.android.googlequicksearchbox",ENGINE="dev.evoker.homeholdcts";
    static final String[] TITLES={"Подключение","Китайская лента","Discover","Поиск","Нижний жест","Кнопка питания","Приложения Google","Разрешения","Работа в фоне"};
    static final String[] SHORT={"Старт","Лента","Discover","Поиск","Жест","Ассистент","Приложения","Доступы","Фон"};
    record GoogleApp(String name,String pkg,String purpose){}
    static final GoogleApp[] APPS={
        new GoogleApp("Google","com.google.android.googlequicksearchbox","Поиск и ассистент"),
        new GoogleApp("Gemini","com.google.android.apps.bard","Ассистент Google"),
        new GoogleApp("Телефон Google","com.google.android.dialer","Звонки"),
        new GoogleApp("Сообщения Google","com.google.android.apps.messaging","SMS и MMS"),
        new GoogleApp("Chrome","com.android.chrome","Браузер"),
        new GoogleApp("Gboard","com.google.android.inputmethod.latin","Клавиатура"),
        new GoogleApp("Контакты Google","com.google.android.contacts","Адресная книга"),
        new GoogleApp("Календарь Google","com.google.android.calendar","События и напоминания"),
        new GoogleApp("Часы Google","com.google.android.deskclock","Будильники и таймеры"),
        new GoogleApp("Калькулятор Google","com.google.android.calculator","Вычисления"),
        new GoogleApp("Gmail","com.google.android.gm","Электронная почта"),
        new GoogleApp("Google Фото","com.google.android.apps.photos","Фото и видео"),
        new GoogleApp("Files Google","com.google.android.apps.nbu.files","Работа с файлами"),
        new GoogleApp("Google Keep","com.google.android.keep","Заметки"),
        new GoogleApp("Google Новости","com.google.android.apps.magazines","Новости"),
        new GoogleApp("Google Переводчик","com.google.android.apps.translate","Перевод"),
        new GoogleApp("Google Lens","com.google.ar.lens","Визуальный поиск")
    };
    record Permission(String name,String label,boolean granted){}
    static boolean installed(Context c,String p){try{return c.getPackageManager().getApplicationInfo(p,0).enabled;}catch(Exception e){return false;}}
    static String version(Context c,String p){try{return c.getPackageManager().getPackageInfo(p,0).versionName;}catch(Exception e){return "не установлено";}}
    static boolean trustedEngine(Context c){try{PackageInfo pi=c.getPackageManager().getPackageInfo(ENGINE,PackageManager.GET_SIGNING_CERTIFICATES);if(pi.getLongVersionCode()!=16030000L||pi.signingInfo==null)return false;for(Signature s:pi.signingInfo.getApkContentsSigners()){String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()));if(hash.equals("e52320218f36db099e6971b0070bba40eec451c1a69f634bb073be26a81f236e"))return true;}return false;}catch(Exception e){return false;}}
    static boolean supportedDiscover(Context c){try{return "PLR110".equals(android.os.Build.MODEL)&&c.getPackageManager().getPackageInfo("com.android.launcher",0).getLongVersionCode()==160060017L;}catch(Exception e){return false;}}
    static boolean allowedGoogle(String p){for(GoogleApp a:APPS)if(a.pkg.equals(p))return true;return false;}
    static List<Permission> permissions(Context c,String pkg){
        if(!allowedGoogle(pkg))throw new IllegalArgumentException("Google app required");List<Permission> out=new ArrayList<>();
        try{PackageInfo pi=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_PERMISSIONS);if(pi.requestedPermissions==null)return out;
            for(int i=0;i<pi.requestedPermissions.length;i++){String n=pi.requestedPermissions[i];if(!n.matches("android\\.permission\\.[A-Z_]+"))continue;try{PermissionInfo p=c.getPackageManager().getPermissionInfo(n,0);if((p.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_DANGEROUS)continue;boolean granted=(pi.requestedPermissionsFlags[i]&PackageInfo.REQUESTED_PERMISSION_GRANTED)!=0;out.add(new Permission(n,p.loadLabel(c.getPackageManager()).toString(),granted));}catch(PackageManager.NameNotFoundException ignored){}}
        }catch(PackageManager.NameNotFoundException ignored){}
        out.sort(Comparator.comparing((Permission p)->p.name.contains("BACKGROUND_LOCATION")).thenComparing(p->p.name));return out;
    }
    static String q(String s){return LocalShell.quote(s);}
    static String check(){return "printf 'native='; if dumpsys activity services "+GOOGLE+" 2>/dev/null | grep 'DrawerOverlayService c:com.android.launcher' >/dev/null; then echo 1; else echo 0; fi\n"
        +"printf 'agent='; content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status 2>/dev/null || echo UNAVAILABLE\n"
        +"for pair in 'dialer android.app.role.DIALER' 'sms android.app.role.SMS' 'browser android.app.role.BROWSER' 'assistant android.app.role.ASSISTANT'; do set -- $pair; printf '%s=' \"$1\"; cmd role get-role-holders --user 0 \"$2\" | tr '\\n' ','; echo; done\n"
        +"printf 'ime='; settings get secure default_input_method\nprintf 'voice='; settings get secure voice_interaction_service\n"
        +"printf 'files_all='; if cmd appops get com.google.android.apps.nbu.files MANAGE_EXTERNAL_STORAGE 2>/dev/null | grep -q 'allow'; then echo 1; else echo 0; fi\n"
        +"printf 'calculator_off='; if pm list packages -d --user 0 com.coloros.calculator | grep -q com.coloros.calculator; then echo 1; else echo 0; fi\n"
        +"printf 'uid='; id -u\n";}
    static String role(String name,String pkg){
        if(!Set.of("DIALER","SMS","BROWSER","ASSISTANT").contains(name)||!allowedGoogle(pkg))throw new IllegalArgumentException();
        return "set -e\n[ -n \"$(pm path --user 0 "+q(pkg)+")\" ]\ncmd role add-role-holder --user 0 android.app.role."+name+" "+q(pkg)+" 0\n";
    }
    static String assistant(){return role("ASSISTANT",GOOGLE)+"settings put secure assistant "+GOOGLE+"/com.google.android.voiceinteraction.GsaVoiceInteractionService\nsettings put secure voice_interaction_service "+GOOGLE+"/com.google.android.voiceinteraction.GsaVoiceInteractionService\n";}
    static String gboard(){return "set -e\nime enable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME\nime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME\n";}
    static String prepareEngine(String activation){return "set -e\n[ -n \"$(pm path --user 0 "+ENGINE+")\" ]\npm grant "+ENGINE+" android.permission.READ_LOGS\npm grant "+ENGINE+" android.permission.POST_NOTIFICATIONS\ncmd appops set "+ENGINE+" SYSTEM_ALERT_WINDOW allow\n"+activation.replace("cat /data/local/tmp/colorgoogle-search.log",":")+"\n";}
    static String grant(Context c,String pkg,List<Permission> selected){
        if(!allowedGoogle(pkg)||selected.isEmpty())throw new IllegalArgumentException("Выбери разрешения");Set<String> allowed=new HashSet<>();for(Permission p:permissions(c,pkg))allowed.add(p.name);
        StringBuilder b=new StringBuilder("failed=0\n");for(Permission p:selected){if(!allowed.contains(p.name)||!p.name.matches("android\\.permission\\.[A-Z_]+"))throw new IllegalArgumentException("Unsupported permission");b.append("pm grant ").append(q(pkg)).append(" ").append(q(p.name)).append("; r=$?; printf 'PERMISSION %s %s\\n' ").append(q(p.name)).append(" \"$r\"; [ \"$r\" -eq 0 ] || failed=1\n");}return b.append("exit \"$failed\"\n").toString();
    }
    static String special(String pkg){
        if(pkg.equals("com.google.android.apps.nbu.files"))return "set -e\ncmd appops set --uid "+pkg+" MANAGE_EXTERNAL_STORAGE allow\n";
        if(pkg.equals("com.google.android.deskclock"))return "set -e\ncmd appops set "+pkg+" USE_FULL_SCREEN_INTENT allow\ndumpsys deviceidle whitelist +"+pkg+"\ncmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND allow\n";
        if(pkg.equals("com.google.android.calendar"))return "set -e\ndumpsys deviceidle whitelist +"+pkg+"\ncmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND allow\n";
        throw new IllegalArgumentException();
    }
    static String background(){StringBuilder s=new StringBuilder("set -e\n");for(String p:new String[]{"dev.local.ace6tdiscover",ENGINE,GOOGLE,"com.google.android.apps.bard","com.google.android.deskclock","com.google.android.calendar"})s.append("if [ -n \"$(pm path --user 0 ").append(q(p)).append(")\" ]; then dumpsys deviceidle whitelist +").append(q(p)).append("; cmd appops set ").append(q(p)).append(" RUN_ANY_IN_BACKGROUND allow; fi\n");return s.toString();}
    static String calculator(boolean disable){return disable?"set -e\n[ -n \"$(pm path --user 0 com.google.android.calculator)\" ]\npm disable-user --user 0 com.coloros.calculator\n":"pm default-state --user 0 com.coloros.calculator\n";}
}
