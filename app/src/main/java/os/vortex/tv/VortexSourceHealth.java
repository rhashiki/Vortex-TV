package os.vortex.tv;

import android.content.Context;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
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

}
