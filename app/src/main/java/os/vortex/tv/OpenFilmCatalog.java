package os.vortex.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Paginated discovery of Creative Commons and CC0 films on Internet Archive.
 * Each media record must declare an open licence and a real MP4 before
 * it is added. Search results alone are never treated as playable.
 *
 * No proxy, protected-player extraction, DRM or authentication bypass.
 */
public final class OpenFilmCatalog {
    private static final String BASE="https://archive.org";
    private static final int SEARCH_ROWS=100;
    private static final int MAX_METADATA=72;

    public static final class Batch {
        public final List<CatalogStore.Item> items;
        public final int nextPage, pages, totalFound;
        Batch(List<CatalogStore.Item> items,int nextPage,int pages,int totalFound){
            this.items=items;this.nextPage=nextPage;this.pages=pages;this.totalFound=totalFound;
        }
    }

    /** Old call remains available to earlier integrations. */
    public List<CatalogStore.Item> fetch(int maxItems) throws Exception {
        return fetchPage(1,maxItems).items;
    }

    public Batch fetchPage(int requestedPage,int maxItems) throws Exception {
        int page=Math.max(1,requestedPage);
        int limit=Math.min(40,Math.max(1,maxItems));
        String query="mediatype:movies AND licenseurl:http*creativecommons.org*";
        String endpoint=BASE+"/advancedsearch.php?q="+URLEncoder.encode(query,"UTF-8")
            +"&fl%5B%5D=identifier&fl%5B%5D=title&rows="+SEARCH_ROWS
            +"&page="+page+"&sort%5B%5D=downloads+desc&output=json";
        JSONObject response=readJson(endpoint,2_000_000);
        JSONObject records=response.optJSONObject("response");
        if(records==null)throw new java.io.IOException("Resposta do catálogo inválida");
        JSONArray docs=records.optJSONArray("docs");
        if(docs==null)throw new java.io.IOException("Itens não encontrados");
        int total=records.optInt("numFound",0);
        int pages=total<=0?1:(int)Math.ceil(total/(double)SEARCH_ROWS);
        ArrayList<CatalogStore.Item> items=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        int examined=0;
        for(int i=0;i<docs.length()&&examined<MAX_METADATA&&items.size()<limit;i++){
            JSONObject d=docs.optJSONObject(i);
            if(d==null)continue;
            String id=d.optString("identifier","");
            if(!id.matches("[a-zA-Z0-9_.-]{3,100}")||!seen.add(id))continue;
            examined++;
            try{
                CatalogStore.Item found=resolve(id);
                if(found!=null)items.add(found);
            }catch(Exception ignored){
                // Network or incomplete metadata failure on a single item must
                // not invalidate the rest of this batch.
            }
        }
        // Do not cycle silently over page 1 once the result set ends.
        int nextPage=page>=pages?pages+1:page+1;
        return new Batch(items,nextPage,pages,total);
    }

    private CatalogStore.Item resolve(String id) throws Exception {
        JSONObject doc=readJson(BASE+"/metadata/"+id,1_800_000);
        JSONObject meta=doc.optJSONObject("metadata");
        if(meta==null)return null;
        String licence=str(meta.opt("licenseurl"));
        if(!(licence.startsWith("https://creativecommons.org/licenses/")
          || licence.startsWith("http://creativecommons.org/licenses/")
          || licence.startsWith("https://creativecommons.org/publicdomain/")
          || licence.startsWith("http://creativecommons.org/publicdomain/")))return null;

        String title=cleanTitle(str(meta.opt("title_pt")));
        if(title.isEmpty())title=cleanTitle(str(meta.opt("title")));
        if(title.isEmpty())return null;

        JSONArray files=doc.optJSONArray("files");
        if(files==null)return null;
        String file=null;
        long bestSize=Long.MAX_VALUE;
        for(int n=0;n<files.length();n++){
            JSONObject f=files.optJSONObject(n);
            if(f==null)continue;
            String name=f.optString("name","");
            String lower=name.toLowerCase(Locale.ROOT);
            if(!lower.endsWith(".mp4")||name.contains("/")||name.contains(".."))continue;
            if(lower.contains("trailer")||lower.contains("sample")||lower.contains("preview"))continue;
            long size=parseSize(f.optString("size","0"));
            if(size<8_000_000L || size>4_000_000_000L)continue;
            String format=f.optString("format","").toLowerCase(Locale.ROOT);
            if(format.contains("sample"))continue;
            if(size<bestSize){bestSize=size;file=name;}
        }
        if(file==null)return null;
        String subject=str(meta.opt("subject"));
        String description=stripHtml(str(meta.opt("description")));
        if(description.length()>300)description=description.substring(0,297)+"...";
        String genre=genreFor(subject+" "+title);
        String year=str(meta.opt("year"));
        if(year.isEmpty()){
            String date=str(meta.opt("date"));
            if(date.length()>=4)year=date.substring(0,4);
        }
        if(!year.matches("\\d{4}"))year="";
        String url=BASE+"/download/"+id+"/"+URLEncoder.encode(file,"UTF-8").replace("+","%20");
        return new CatalogStore.Item(title,url,"","Filmes • "+genre,
            BASE+"/services/img/"+id,"file",genre,
            "Internet Archive · Creative Commons",
            (description.isEmpty()?"":description+"\n")+"Licença declarada: "+licence,year);
    }

    /**
     * Remove upload labels, quality tags and file extensions without inventing
     * a translated or commercial title. Unreliable names are excluded.
     */
    public static String cleanTitle(String raw) {
        if(raw==null)return "";
        String title=stripHtml(raw).replace("&amp;","&").replace("&quot;","\"")
            .replace("&#39;","'").replace("&nbsp;"," ");
        title=title.replaceAll("(?i)\\.(mp4|mkv|avi|webm)$","");
        title=title.replace('_',' ');
        title=title.replaceAll("(?i)(?:\\[|\\()(?:1080p|720p|480p|2160p|4k|hdr|hd|fhd|bluray|brrip|dvdrip|webrip|web-dl|x264|x265|h264|hevc)(?:\\]|\\))"," ");
        title=title.replaceAll("(?i)\\s*[-|:]\\s*(?:full movie|complete movie|filme completo|filme dublado|full film|legendado|dublado)(?:\\s+\\w+){0,2}\\s*$","");
        title=title.replaceAll("(?i)^(?:full movie|complete movie|filme completo|filme dublado|full film)\\s*[:\\-|]\\s*","");
        title=title.replaceAll("\\s+"," ").replaceAll("^[\\s\\-_|:]+|[\\s\\-_|:]+$","").trim();
        if(title.length()<3||title.length()>125)return "";
        if(title.matches("(?i)^(?:untitled|test|teste|video|vídeo|movie|filme|sample|clip|upload)(?:\\s*[0-9]+)?$"))return "";
        int letters=0,digits=0;
        for(int i=0;i<title.length();i++){
            char c=title.charAt(i);
            if(Character.isLetter(c))letters++;
            if(Character.isDigit(c))digits++;
        }
        if(letters<3 || digits>letters*2)return "";
        return title;
    }
    private static String genreFor(String tags){
        String t=tags.toLowerCase(Locale.ROOT);
        if(t.matches("(?s).*(documentary|documentário|documentario|nonfiction|non-fiction|educational).*"))return "Documentário";
        if(t.matches("(?s).*(animation|animated|animação|animacao|cartoon).*"))return "Animação";
        if(t.matches("(?s).*(horror|terror|zombie|monster).*"))return "Terror";
        if(t.matches("(?s).*(science fiction|sci-fi|ficção científica|ficcao cientifica).*"))return "Ficção científica";
        if(t.matches("(?s).*(lgbt|queer|lesbian|gay|transgender).*"))return "LGBT";
        if(t.matches("(?s).*(\\bwar\\b|warfare|guerra|battle).*"))return "Guerra";
        if(t.matches("(?s).*(romance|romantic|amor).*"))return "Romance";
        if(t.matches("(?s).*(fantasy|fantasia).*"))return "Fantasia";
        if(t.matches("(?s).*(suspense|thriller|mystery|mistério).*"))return "Suspense";
        if(t.matches("(?s).*(adventure|aventura).*"))return "Aventura";
        return "Outros";
    }
    private static long parseSize(String s){try{return Long.parseLong(s);}catch(Exception ex){return 0L;}}
    private static String stripHtml(String s){return s.replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();}
    private static String str(Object obj){
        if(obj==null||obj==JSONObject.NULL)return "";
        if(obj instanceof JSONArray){JSONArray a=(JSONArray)obj;return a.length()>0?a.optString(0):"";}
        return String.valueOf(obj).trim();
    }
    private static JSONObject readJson(String url,int maxBytes)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","VortexTV/0.2.5 (open-content-indexer)");
        try{
            if(c.getResponseCode()!=200)throw new java.io.IOException("Catálogo HTTP "+c.getResponseCode());
            if(c.getContentLengthLong()>maxBytes)throw new java.io.IOException("Catálogo excede limite");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int count,total=0;
                while((count=in.read(buf))!=-1){
                    total+=count;
                    if(total>maxBytes)throw new java.io.IOException("Resposta excede limite");
                    out.write(buf,0,count);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
}
