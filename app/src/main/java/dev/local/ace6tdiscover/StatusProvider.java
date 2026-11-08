// SPDX-License-Identifier: GPL-3.0-only
package dev.local.ace6tdiscover;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
/** Read-only diagnostics for adb shell. No activation, pairing keys, or codes. */
public final class StatusProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public Bundle call(String method,String arg,Bundle extras){
        if(!"status".equals(method))throw new IllegalArgumentException("Read-only status");
        android.content.SharedPreferences p=getContext().getSharedPreferences("state",0);
        Bundle out=new Bundle();out.putString("status",p.getString("status","Not started"));
        out.putBoolean("auto",p.getBoolean("auto",false));out.putBoolean("paired",p.getBoolean("paired",false));
        out.putInt("restores",p.getInt("restores",0));
        android.content.SharedPreferences w=getContext().getSharedPreferences("wizard",0);
        out.putLong("lastCheck",w.getLong("checked",0));out.putInt("wizardPage",w.getInt("page",0));
        try{String panel=new org.json.JSONObject(w.getString("facts","{}")).optString("native","unknown");out.putString("panelCheck",panel.substring(0,Math.min(panel.length(),160)));}catch(Exception ignored){}
        return out;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
