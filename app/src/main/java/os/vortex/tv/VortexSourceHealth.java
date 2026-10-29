package os.vortex.tv;

import android.content.Context;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native catalog health: the TV Box reports actual Media3 playback outcomes.
 * No media URL, login, credential or stream is transmitted in a report; only
 * the MD5 source identifier (not an authentication secret) and a boolean.
 * Network requests never run on the UI thread.
 */
public final class VortexSourceHealth {
    private static final String ENDPOINT =
        "https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-family-health";
    private VortexSourceHealth(){}

    public static String hash(String url){
        try{
            byte[] digest=MessageDigest.getInstance("MD5").digest(
                url.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder(32);
            for(byte b:digest)out.append(String.format(Locale.US,"%02x",b&0xff));
            return out.toString();
        }catch(Exception e){return "";}
    }

    /** Non-blocking best-effort feedback; local player failover always works offline. */
    public static void reportAsync(Context context,String mediaUrl,boolean successful){
        Context app=context.getApplicationContext();
        String key=NativeCatalogConfig.readFamilyAccess(app);
        if(key.isEmpty()||mediaUrl==null)return;
        String urlHash=hash(mediaUrl);
        if(urlHash.length()!=32)return;
        new Thread(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                connection.setConnectTimeout(6500);
                connection.setReadTimeout(7000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                connection.setRequestProperty("x-vortex-family-key",key);
                byte[] json=new JSONObject().put("hash",urlHash)
                    .put("success",successful).toString().getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(json.length);
                try(java.io.OutputStream out=connection.getOutputStream()){
                    out.write(json);
                }
                connection.getResponseCode();
            }catch(Exception ignored){
                // Network failure must never interrupt playback or display credentials.
            }finally{if(connection!=null)connection.disconnect();}
        },"Vortex-Playback-Health").start();
    }

    private static String readLimited(InputStream stream,int limit)throws Exception{
        try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n,total=0;
            while((n=stream.read(buffer))!=-1){
                total+=n;if(total>limit)throw new java.io.IOException("Health page too large");
                output.write(buffer,0,n);
            }
            return output.toString("UTF-8");
        }
    }

    /** Atomically replace searching tags only after all pages succeed. */
    public static int refresh(Context context,CatalogStore catalog,String familyCode)
        throws Exception{
        if(familyCode==null||!familyCode.matches("[A-F0-9]{20}"))
            throw new java.io.IOException("No OEM access");
        Set<String> hashes=new HashSet<>();
        long cursor=0L;boolean complete=false;
        for(int page=0;page<400;page++){
            HttpURLConnection conn=(HttpURLConnection)new URL(
                ENDPOINT+"?after="+cursor).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(11000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("x-vortex-family-key",familyCode);
            try{
                if(conn.getResponseCode()!=200)
                    throw new java.io.IOException("Health temporarily unavailable");
                JSONObject body;
                try(InputStream in=conn.getInputStream()){
                    body=new JSONObject(readLimited(in,180000));
                }
                JSONArray items=body.getJSONArray("items");
                if(items.length()>300)throw new java.io.IOException("Invalid health batch");
                for(int i=0;i<items.length();i++){
                    String hash=items.getJSONObject(i).optString("source_hash","");
                    if(hash.matches("[a-f0-9]{32}"))hashes.add(hash);
                }
                long next=body.getLong("next");
                boolean more=body.getBoolean("has_more");
                if(next<cursor||(more&&next==cursor))
                    throw new java.io.IOException("Invalid health cursor");
                cursor=next;
                if(!more){complete=true;break;}
            }finally{conn.disconnect();}
        }
        if(!complete)throw new java.io.IOException("Health page limit");
        catalog.replaceRemoteSearching(hashes);
        return hashes.size();
    }
}
