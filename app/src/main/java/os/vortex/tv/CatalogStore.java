package os.vortex.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import os.vortex.tv.integration.M3uPlaylistParser;

/** An offline, private on-device catalog. Only imports user-supplied playlists. */
public final class CatalogStore {
    public static final class Item {
        public final String name, url, tvgId, group, logo, format, genre, provider, description, year;
        public Item(String name, String url, String tvgId, String group, String logo, String format) {
            this(name,url,tvgId,group,logo,format,"","","","");
        }
        public Item(String name,String url,String tvgId,String group,String logo,String format,
                    String genre,String provider,String description,String year){
            this.name=name;this.url=url;this.tvgId=tvgId;this.group=group;this.logo=logo;this.format=format;
            this.genre=genre;this.provider=provider;this.description=description;this.year=year;
        }
        public boolean isFilm() {return "youtube-movie".equals(format) || (!isLive() && (group.toLowerCase().matches(".*(filme|movie|cinema|vod).*?") || "Filmes".equalsIgnoreCase(provider)));}
        public boolean isSeries(){ return "youtube-series".equals(format) || (!isLive() && group.toLowerCase().matches(".*(série|serie|series|temporada).*?")); }
        public boolean isDoc(){ return "youtube-doc".equals(format) || (!isLive() && group.toLowerCase().matches(".*(document|nature|história|historia).*?")); }
        public boolean isVideo(){return group.equalsIgnoreCase("Vídeos");}
        public boolean isLive(){
            if("youtube-live".equals(format))return true;
            if("youtube-movie".equals(format)||"youtube-doc".equals(format)
                ||"youtube-series".equals(format))return false;
            if(group.toLowerCase(java.util.Locale.ROOT).matches(".*(filme|movie|cinema|vod|série|serie|temporada|document).*"))return false;
            if("hls".equals(format) || "dash".equals(format))return true;
            if("Vídeos".equalsIgnoreCase(group))return false;
            if("file".equals(format) || "Filmes".equalsIgnoreCase(provider))return false;
            return !(group.toLowerCase().matches(".*(filme|movie|cinema|vod|série|serie|temporada|document|nature).*?"));
        }
    }
    private static final String KEY="catalog";
    private static final String FAV="favorites";
    private static final String CLOUD="cloud_items_v1";
    private static final int MAX_ITEMS=2500;
    private final SharedPreferences prefs;
    public CatalogStore(Context ctx){
        prefs=ctx.getSharedPreferences("vortex_catalog",Context.MODE_PRIVATE);
        repairLegacyBundledSources();
        initializeSeeds(ctx);
        repairArchiveTitles();
    }

    /**
     * Upgrade only exact bundled demo URLs. Never rewrite user imports, names,
     * unrelated URLs, or wipe favorites. Migrates old demo favorites as well.
     */
    private void repairLegacyBundledSources(){
        if(prefs.getBoolean("repair_demo_v3",false))return;
        final String base="https://storage.googleapis.com/gtv-videos-bucket/sample/";
        final String[] names={"Big Buck Bunny","Sintel","Tears of Steel","Elephants Dream","TV Câmara"};
        final String[] old={
            base+"BigBuckBunny.mp4",base+"Sintel.mp4",base+"TearsOfSteel.mp4",
            base+"ElephantsDream.mp4","https://stream3.camara.leg.br/tv1/manifest.m3u8"};
        final String[] replacement={
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "https://archive.org/download/Sintel/sintel-2048-surround_512kb.mp4",
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            "https://archive.org/download/ElephantsDream/ed_hd_512kb.mp4",
            "https://stream3.camara.gov.br/tv1/manifest.m3u8"};
        final String[] posters={
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Big_buck_bunny_poster_big.jpg?width=500",
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Sintel_poster.jpg?width=500",
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Tos-poster.png?width=500",
            "https://archive.org/services/img/ElephantsDream",""};
        List<Item> stored=loadLocal();
        ArrayList<Item> updated=new ArrayList<>();
        Set<String> favorites=new HashSet<>(prefs.getStringSet(FAV,new HashSet<String>()));
        boolean changed=false;
        for(Item item:stored){
            for(int n=0;n<old.length;n++){
                if(!old[n].equals(item.url)||!names[n].equals(item.name))continue;
                String poster=posters[n].isEmpty()?item.logo:posters[n];
                String newFormat=replacement[n].endsWith(".m3u8")?"hls":"file";
                item=new Item(item.name,replacement[n],item.tvgId,item.group,poster,
                    newFormat,item.genre,item.provider,item.description,item.year);
                if(favorites.remove(old[n]))favorites.add(replacement[n]);
                changed=true;
                break;
            }
            updated.add(item);
        }
        if(changed){
            // Preserve user collections while avoiding duplicated sources.
            ArrayList<Item> unique=new ArrayList<>();
            Set<String> urls=new HashSet<>();
            for(Item item:updated)if(urls.add(item.url))unique.add(item);
            save(unique);
            prefs.edit().putStringSet(FAV,favorites).apply();
        }
        prefs.edit().putBoolean("repair_demo_v3",true).apply();
    }

    private void initializeSeeds(Context ctx) {
        if(prefs.getBoolean("seed_v3",false)) return;
        try(java.io.InputStream stream=ctx.getAssets().open("seed_catalog.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] bytes=new byte[8192];int n;
            while((n=stream.read(bytes))!=-1)out.write(bytes,0,n);
            JSONArray base=new JSONArray(out.toString("UTF-8"));
            JSONArray existing=new JSONArray(prefs.getString(KEY,"[]"));
            Set<String> known=new HashSet<>();
            for(int i=0;i<existing.length();i++){
                JSONObject j=existing.optJSONObject(i);if(j!=null)known.add(j.optString("url"));
            }
            for(int i=0;i<base.length()&&existing.length()<MAX_ITEMS;i++) {
                JSONObject item=base.getJSONObject(i);
                String url=item.optString("url");
                if(url.isEmpty())continue;
                if(known.add(url)){existing.put(item);continue;}
                // Upgrade metadata of earlier bundled titles without replacing
                // users' stream URLs or their stored favorites.
                for(int entryIndex=0;entryIndex<existing.length();entryIndex++){
                    JSONObject old=existing.optJSONObject(entryIndex);
                    if(old!=null&&url.equals(old.optString("url"))){
                        for(String field:new String[]{"genre","provider","description","year"}){
                            if(old.optString(field).isEmpty()&&!item.optString(field).isEmpty())
                                old.put(field,item.optString(field));
                        }
                        if(old.optString("logo").isEmpty()&&!item.optString("logo").isEmpty())
                            old.put("logo",item.optString("logo"));
                        break;
                    }
                }
            }
            prefs.edit().putString(KEY,existing.toString()).putBoolean("seed_v1",true)
                .putBoolean("seed_v2",true).putBoolean("seed_v3",true).apply();
        } catch(Exception e) {
            // Never overwrite an existing catalog on failed seed read.
            android.util.Log.w("VortexTV","Bundled catalog unavailable",e);
        }
    }
    /** Clean only machine-imported Archive titles once; never rewrite user titles. */
    private void repairArchiveTitles(){
        if(prefs.getBoolean("archive_title_clean_v1",false))return;
        List<Item> existing=loadLocal();
        List<Item> updated=new ArrayList<>();
        boolean changed=false;
        for(Item item:existing){
            if(item.provider.startsWith("Internet Archive")){
                String clean=OpenFilmCatalog.cleanTitle(item.name);
                if(!clean.isEmpty()&&!clean.equals(item.name)){
                    item=new Item(clean,item.url,item.tvgId,item.group,item.logo,item.format,
                        item.genre,item.provider,item.description,item.year);
                    changed=true;
                }
            }
            updated.add(item);
        }
        if(changed)save(updated);
        prefs.edit().putBoolean("archive_title_clean_v1",true).apply();
    }

    /** Persist a corrected display title without changing the underlying media URL. */
    public boolean rename(String url,String newName){
        if(newName==null)return false;
        String title=newName.trim().replaceAll("[\\p{Cntrl}]+"," ").trim();
        if(title.length()<2||title.length()>120)return false;
        List<Item> all=loadLocal();
        boolean changed=false;
        for(int p=0;p<all.size();p++){
            Item item=all.get(p);
            if(item.url.equals(url)){
                all.set(p,new Item(title,item.url,item.tvgId,item.group,item.logo,item.format,
                    item.genre,item.provider,item.description,item.year));
                changed=true;
                break;
            }
        }
        if(changed)save(all);
        return changed;
    }

    private List<Item> loadFrom(String key){
        ArrayList<Item> result=new ArrayList<>();
        try {
            JSONArray data=new JSONArray(prefs.getString(key,"[]"));
            for(int i=0;i<data.length() && i<MAX_ITEMS;i++){
                JSONObject j=data.optJSONObject(i);
                if(j==null)continue;
                result.add(new Item(j.optString("name"),j.optString("url"),j.optString("tvgId"),j.optString("group"),j.optString("logo"),j.optString("format"),j.optString("genre"),j.optString("provider"),j.optString("description"),j.optString("year")));
            }
        }catch(Exception ignored){}
        return result;
    }
    private List<Item> loadLocal(){return loadFrom(KEY);}
    /** Cloud entries are isolated from personal imports, preserving local edits and favorites. */
    public List<Item> load(){
        List<Item> result=loadLocal();
        Set<String> urls=new HashSet<>();
        for(Item item:result)urls.add(item.url);
        for(Item item:loadFrom(CLOUD)){
            if(result.size()>=MAX_ITEMS)break;
            if(urls.add(item.url))result.add(item);
        }
        return result;
    }
    /** Atomic refresh: a malformed remote response never erases the previous cache. */
    public int replaceRemote(List<Item> remote){
        if(remote==null)throw new IllegalArgumentException("Catálogo ausente");
        List<Item> accepted=new ArrayList<>();
        Set<String> urls=new HashSet<>();
        for(Item item:remote){
            if(accepted.size()>=MAX_ITEMS)break;
            if(item!=null && item.url.startsWith("https://") && urls.add(item.url))
                accepted.add(item);
        }
        saveItems(CLOUD,accepted);
        return accepted.size();
    }
    public int append(List<M3uPlaylistParser.Channel> channels){
        List<Item> catalog=loadLocal();
        Set<String> urls=new HashSet<>(); for(Item i:catalog)urls.add(i.url);
        int added=0;
        for(M3uPlaylistParser.Channel ch:channels){
            if(catalog.size()>=MAX_ITEMS) break;
            if(urls.add(ch.url)){catalog.add(new Item(ch.name,ch.url,ch.tvgId,ch.group,ch.logo,ch.format));added++;}
        }
        save(catalog); return added;
    }
    public int appendItems(List<Item> incoming){
        List<Item> stored=loadLocal();Set<String> urls=new HashSet<>();
        for(Item item:stored)urls.add(item.url);
        int count=0;
        for(Item item:incoming){
            if(stored.size()>=MAX_ITEMS)break;
            if(item.url.startsWith("https://")&&urls.add(item.url)){stored.add(item);count++;}
        }
        if(count>0)save(stored);
        return count;
    }
    public void addDirect(String name,String url){
        List<Item> items=loadLocal();
        for(Item i:items)if(i.url.equals(url))return;
        if(items.size()<MAX_ITEMS){items.add(new Item(name,url,"","Vídeos","",M3uPlaylistParser.kind(url)));save(items);}
    }
    private void save(List<Item> items){saveItems(KEY,items);}
    private void saveItems(String key,List<Item> items){
        JSONArray data=new JSONArray();
        for(Item i:items){
            JSONObject j=new JSONObject();
            try{j.put("name",i.name);j.put("url",i.url);j.put("tvgId",i.tvgId);j.put("group",i.group);j.put("logo",i.logo);j.put("format",i.format);j.put("genre",i.genre);j.put("provider",i.provider);j.put("description",i.description);j.put("year",i.year);data.put(j);}catch(Exception ignored){}
        }
        if(!prefs.edit().putString(key,data.toString()).commit())
            throw new IllegalStateException("Falha ao salvar catálogo");
    }
    public boolean isFavorite(String url){return prefs.getStringSet(FAV,new HashSet<String>()).contains(url);}
    public void toggleFavorite(String url){
        Set<String> fav=new HashSet<>(prefs.getStringSet(FAV,new HashSet<String>()));
        if(fav.contains(url))fav.remove(url);else fav.add(url);
        prefs.edit().putStringSet(FAV,fav).apply();
    }
    public void clear(){prefs.edit().remove(KEY).remove(CLOUD).remove(FAV).apply();}
}
