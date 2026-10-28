package os.vortex.tv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pulls a small, curated JSON feed. It never downloads/transmits video data.
 * Local imports and favorites remain in the separate device-owned catalog.
 */
public final class RemoteCatalogSync {
    private static final int MAX_BYTES=1_048_576;
    private static final int MAX_ITEMS=2000;
    private RemoteCatalogSync(){}

    public static final class Result {
        public final int received,accepted;
        Result(int received,int accepted){this.received=received;this.accepted=accepted;}
    }

    private static String read(InputStream input,int limit) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];
            int n,total=0;
            while((n=in.read(buf))!=-1){
                total+=n;
                if(total>limit)throw new IllegalArgumentException("Catálogo maior que o limite permitido");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }

    private static boolean allowedEndpoint(String address){
        try {
            URI uri=new URI(address);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null)return false;
            String host=uri.getHost();
            if(host==null)return false;
            host=host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("raw.githubusercontent.com") || host.endsWith(".supabase.co");
        }catch(Exception e){return false;}
    }

    public static Result refresh(Context context,CatalogStore store) throws Exception {
        JSONObject config=new JSONObject(read(context.getAssets().open("cloud_config.json"),8192));
        String endpoint=config.optString("catalog_url","");
        if(!allowedEndpoint(endpoint))throw new IllegalArgumentException("Endpoint Vortex Cloud inválido");

        HttpURLConnection conn=(HttpURLConnection)new URL(endpoint).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        try {
            if(conn.getResponseCode()!=200)throw new IllegalStateException("HTTP "+conn.getResponseCode());
            if(conn.getContentLengthLong()>MAX_BYTES)throw new IllegalStateException("Resposta muito grande");
            JSONObject payload=new JSONObject(read(conn.getInputStream(),MAX_BYTES));
            if(payload.optInt("version",-1)!=1)throw new IllegalStateException("Versão de catálogo incompatível");
            JSONArray incoming=payload.optJSONArray("items");
            if(incoming==null || incoming.length()>MAX_ITEMS)
                throw new IllegalStateException("Catálogo remoto malformado");
            List<CatalogStore.Item> approved=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(int i=0;i<incoming.length();i++){
                JSONObject item=incoming.optJSONObject(i);
                if(item==null)continue;
                // HTTP or manifest tests alone never imply decoded playback or media rights.
                if(!"playback_ok".equals(item.optString("status")))continue;
                if(!"authorized".equals(item.optString("rights_status")))continue;
                String name=item.optString("name","").trim();
                String url=item.optString("url","").trim();
                if(name.length()<2||name.length()>120||url.length()>2048
                    ||!url.startsWith("https://")||!seen.add(url))continue;
                try {
                    URI media=new URI(url);
                    if(media.getHost()==null||media.getUserInfo()!=null)continue;
                }catch(Exception ignored){continue;}
                approved.add(new CatalogStore.Item(
                    name,url,item.optString("tvgId",""),item.optString("group",""),
                    item.optString("logo",""),item.optString("format","auto"),
                    item.optString("genre",""),item.optString("provider","Vortex TV Cloud"),
                    item.optString("description",""),item.optString("year","")));
            }
            return new Result(incoming.length(),store.replaceRemote(approved));
        }finally{conn.disconnect();}
    }
}
