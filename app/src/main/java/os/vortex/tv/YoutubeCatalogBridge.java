package os.vortex.tv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** YouTube metadata -> Vortex entries. No extraction of protected media. */
public final class YoutubeCatalogBridge {
    private static final String INDEX_URL="https://raw.githubusercontent.com/rhashiki/Vortex-TV/main/catalog/youtube/index.json";
    private YoutubeCatalogBridge(){}
    public static List<CatalogStore.Item> loadSeed(Context c) throws Exception {
        try(InputStream in=c.getAssets().open("youtube_seed.json")){
            return parse(readJson(in,512_000),true);
        }
    }
    public static List<CatalogStore.Item> fetchIndex() throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(INDEX_URL).openConnection();
        conn.setConnectTimeout(8500);conn.setReadTimeout(14000);conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        try{
            if(conn.getResponseCode()!=200)throw new java.io.IOException("YouTube index HTTP "+conn.getResponseCode());
            try(InputStream in=conn.getInputStream()){
                return parse(readJson(in,4_000_000),false);
            }
        }finally{conn.disconnect();}
    }
    private static JSONObject readJson(InputStream in,int limit)throws Exception{
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int total=0,count;
            while((count=in.read(b))!=-1){total+=count;if(total>limit)throw new java.io.IOException("Índice excede tamanho máximo");out.write(b,0,count);}
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{11}");}
    private static String genre(String group){return group==null||group.trim().isEmpty()?"Outros":group;}
    private static String safePoster(String url,String id){
        if(url!=null&&url.startsWith("https://i.ytimg.com/"))return url;
        return "https://i.ytimg.com/vi/"+id+"/hqdefault.jpg";
    }
    private static List<CatalogStore.Item> parse(JSONObject index,boolean seed){
        JSONArray all=index.optJSONArray("items");
        ArrayList<CatalogStore.Item> out=new ArrayList<>();
        if(all==null)return out;
        for(int i=0;i<all.length();i++){
            JSONObject e=all.optJSONObject(i);
            if(e==null)continue;
            String id=e.optString("video_id",e.optString("youtube_id",""));
            if(!validId(id))continue;
            String title=e.optString("name","").trim();
            if(title.length()<3||title.length()>170)continue;
            String kind=seed?"movie":e.optString("media_type","movie");
            String format="channel".equals(kind)?"youtube-live":
                "documentary".equals(kind)?"youtube-doc":"youtube-movie";
            String g=genre(e.optString("genre",seed?"Filmes":"Outros"));
            String group="youtube-live".equals(format)?"TV YouTube • "+g:
                "youtube-doc".equals(format)?"Documentários • "+g:"Filmes • "+g;
            String rights=e.optString("rights_status",e.optString("status","NÃO VERIFICADO"));
            String descr=e.optString("description","").trim();
            String status=e.optString("playback_status","SEM TESTE DE REPRODUÇÃO");
            String note="YouTube · "+rights+" · "+status;
            if(!descr.isEmpty())note+="\n"+descr;
            String yr=e.optString("year","");
            // For generated entries year_verified must be true before using a year.
            if(!seed){yr=e.optBoolean("year_verified",false)?e.optString("claimed_production_year",""):"";}
            out.add(new CatalogStore.Item(title,"https://www.youtube.com/watch?v="+id,"",group,
                safePoster(e.optString("poster",""),id),format,g,
                e.optString("provider","YouTube"),note,yr));
        }
        return out;
    }
}