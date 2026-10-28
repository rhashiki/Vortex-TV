package os.vortex.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Discovers independently-hosted Creative Commons movies without copying media.
 * This is an optional catalog source, not a proxy, extractor or DRM bypass.
 * Search listing alone is insufficient: only items with a named MP4 file and
 * a declared open license become playable catalog entries.
 */
public final class OpenFilmCatalog {
    private static final String BASE="https://archive.org";
    private static final int MAX_METADATA=30;

    public List<CatalogStore.Item> fetch(int maxItems) throws Exception {
        maxItems=Math.min(Math.max(1,maxItems),20);
        String query="mediatype:movies AND licenseurl:http*creativecommons.org*";
        String endpoint=BASE+"/advancedsearch.php?q="+URLEncoder.encode(query,"UTF-8")
            +"&fl%5B%5D=identifier&fl%5B%5D=title&rows=40&page=1"
            +"&sort%5B%5D=downloads+desc&output=json";
        JSONObject response=readJson(endpoint,1_800_000);
        JSONArray docs=response.optJSONObject("response")==null?null:response.getJSONObject("response").optJSONArray("docs");
        if(docs==null)throw new java.io.IOException("Resposta de catálogo inválida");
        ArrayList<CatalogStore.Item> items=new ArrayList<>();
        for(int i=0;i<docs.length()&&i<MAX_METADATA&&items.size()<maxItems;i++){
            JSONObject doc=docs.optJSONObject(i);if(doc==null)continue;
            String id=doc.optString("identifier","");
            if(!id.matches("[a-zA-Z0-9_.-]{3,100}"))continue;
            try {
                CatalogStore.Item item=resolve(id);
                if(item!=null)items.add(item);
            }catch(Exception ignored){/* One inaccessible item cannot stop the entire catalog */}
        }
        return items;
    }
    private CatalogStore.Item resolve(String id) throws Exception {
        JSONObject doc=readJson(BASE+"/metadata/"+id,1_500_000);
        JSONObject meta=doc.optJSONObject("metadata");if(meta==null)return null;
        String license=str(meta.opt("licenseurl"));
        // Public metadata is not always legally reliable. Be conservative:
        // require an explicit license URL rather than merely 'free' in a title.
        if(!(license.startsWith("https://creativecommons.org/licenses/")
          || license.startsWith("http://creativecommons.org/licenses/")
          || license.startsWith("https://creativecommons.org/publicdomain/")
          || license.startsWith("http://creativecommons.org/publicdomain/")))return null;
        JSONArray files=doc.optJSONArray("files");if(files==null)return null;
        String file=null;long bestSize=Long.MAX_VALUE;
        for(int n=0;n<files.length();n++){
            JSONObject f=files.optJSONObject(n);if(f==null)continue;
            String name=f.optString("name","");
            if(!name.toLowerCase(Locale.ROOT).endsWith(".mp4")||name.contains("/"))continue;
            long size=parseSize(f.optString("size","0"));
            if(size<2_000_000L || size>950_000_000L)continue;
            String format=f.optString("format","").toLowerCase(Locale.ROOT);
            if(format.contains("sample")||name.toLowerCase(Locale.ROOT).contains("trailer"))continue;
            if(size<bestSize){bestSize=size;file=name;}
        }
        if(file==null)return null;
        String title=str(meta.opt("title"));if(title.length()<3)return null;
        String subject=str(meta.opt("subject"));
        String description=stripHtml(str(meta.opt("description")));
        if(description.length()>250)description=description.substring(0,247)+"...";
        String genre=genreFor(subject+" "+title+" "+description);
        String group="Filmes • "+genre;
        String year=str(meta.opt("year"));
        if(year.isEmpty()){String date=str(meta.opt("date"));if(date.length()>=4)year=date.substring(0,4);}
        if(!year.matches("\\d{4}"))year="";
        String url=BASE+"/download/"+id+"/"+URLEncoder.encode(file,"UTF-8").replace("+","%20");
        String poster=BASE+"/services/img/"+id;
        return new CatalogStore.Item(title,url,"",group,poster,"file",genre,
            "Internet Archive · Creative Commons",description+"\nLicença: "+license,year);
    }
    private static String genreFor(String tags){
        tags=tags.toLowerCase(Locale.ROOT);
        if(tags.matches(".*(documentary|documentário|documentario|nonfiction|educational).*"))return "Documentário";
        if(tags.matches(".*(animation|animated|animação|animacao|cartoon).*"))return "Animação";
        if(tags.matches(".*(horror|terror|monster|zombie).*"))return "Terror";
        if(tags.matches(".*(science fiction|sci.fi|ficção científica|space).*"))return "Ficção científica";
        if(tags.matches(".*(lgbt|queer|gay|lesbian).*"))return "LGBT";
        if(tags.matches(".*(war|warfare|guerra).*"))return "Guerra";
        if(tags.matches(".*(romance|romantic|amor).*"))return "Romance";
        if(tags.matches(".*(fantasy|fantasia).*"))return "Fantasia";
        if(tags.matches(".*(suspense|thriller|mystery).*"))return "Suspense";
        if(tags.matches(".*(adventure|aventura).*"))return "Aventura";
        return "Drama";
    }
    private static long parseSize(String s){try{return Long.parseLong(s);}catch(Exception ignored){return 0;}}
    private static String stripHtml(String s){return s.replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();}
    private static String str(Object obj){
        if(obj==null||obj==JSONObject.NULL)return "";
        if(obj instanceof JSONArray){JSONArray a=(JSONArray)obj;return a.length()>0?a.optString(0):"";}
        return String.valueOf(obj).trim();
    }
    private static JSONObject readJson(String url,int maxBytes)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(10000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","VortexTV/0.2.4 (+public-open-content-indexer)");
        try{
            if(c.getResponseCode()!=200)throw new java.io.IOException("HTTP "+c.getResponseCode());
            if(c.getContentLengthLong()>maxBytes)throw new java.io.IOException("Catálogo grande demais");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int count,total=0;
                while((count=in.read(buf))!=-1){total+=count;if(total>maxBytes)throw new java.io.IOException("Limite de catálogo atingido");out.write(buf,0,count);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
}
