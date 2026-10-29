package os.vortex.tv;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import os.vortex.tv.integration.M3uPlaylistParser;

/**
 * Local-only source inventory. Do not upload IPTV URLs: some contain username/password.
 * Reads/writes are intentionally bounded for Android 7.1 boxes with 1 GB RAM.
 */
public final class ImportedSourceStore extends SQLiteOpenHelper {
    private static final String DB_NAME="vortex_private_sources.db";
    private static final int DB_VERSION=2;
    private static final int MAX_ROWS=120000;
    public ImportedSourceStore(Context context){super(context,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS sources("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"identity_key TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL UNIQUE,"
            +"tvg_id TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT '',"
            +"logo TEXT NOT NULL DEFAULT '', format TEXT NOT NULL DEFAULT 'auto',"
            +"source_label TEXT NOT NULL DEFAULT '', last_good_ms INTEGER NOT NULL DEFAULT 0,"
            +"last_bad_ms INTEGER NOT NULL DEFAULT 0, failure_count INTEGER NOT NULL DEFAULT 0,"
            +"remote_status TEXT NOT NULL DEFAULT 'candidate')");
        db.execSQL("CREATE INDEX IF NOT EXISTS source_identity_index ON sources(identity_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS source_display_index ON sources(name)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2)db.execSQL(
            "ALTER TABLE sources ADD COLUMN remote_status TEXT NOT NULL DEFAULT 'candidate'");
    }

    public static final class ImportResult {
        public final int scanned,added,total;
        ImportResult(int scanned,int added,int total){
            this.scanned=scanned;this.added=added;this.total=total;
        }
    }
    public synchronized ImportResult importM3u(Reader reader,String sourceLabel) throws IOException {
        SQLiteDatabase db=getWritableDatabase();
        int previous=count(db);
        if(previous>=MAX_ROWS)throw new IOException("Limite de fontes locais atingido");
        final int[] scanned={0};
        final int[] inserted={0};
        final String label=sourceLabel==null?"Arquivo local":
            sourceLabel.replaceAll("[^A-Za-z0-9_. -]","").substring(
                0,Math.min(60,sourceLabel.replaceAll("[^A-Za-z0-9_. -]","").length()));
        db.beginTransaction();
        try {
            SQLiteStatement stmt=db.compileStatement(
                "INSERT OR IGNORE INTO sources(identity_key,name,url,tvg_id,category,logo,format,source_label,remote_status)"
                +" VALUES(?,?,?,?,?,?,?,?,?)");
            SQLiteStatement status=db.compileStatement(
                "UPDATE sources SET remote_status=?,logo=CASE WHEN ?<>'' THEN ? ELSE logo END,"
                +"category=CASE WHEN category='' THEN ? ELSE category END WHERE url=?");
            try {
                new M3uPlaylistParser().parseToSink(reader,item->{
                    scanned[0]++;
                    if(previous+inserted[0]>=MAX_ROWS)
                        throw new IllegalArgumentException("Limite de 120 mil fontes");
                    String key=SourceIdentity.key(
                        item.name,item.group,item.tvgId,item.format,item.url);
                    stmt.clearBindings();
                    stmt.bindString(1,key);
                    stmt.bindString(2,item.name);
                    stmt.bindString(3,item.url);
                    stmt.bindString(4,item.tvgId);
                    stmt.bindString(5,item.group);
                    stmt.bindString(6,item.logo);
                    stmt.bindString(7,item.format);
                    stmt.bindString(8,label);
                    stmt.bindString(9,item.remoteStatus.isEmpty()?"candidate":item.remoteStatus);
                    if(stmt.executeInsert()!=-1)inserted[0]++;
                    if(!item.remoteStatus.isEmpty()){
                        status.clearBindings();
                        status.bindString(1,item.remoteStatus);
                        status.bindString(2,item.logo);
                        status.bindString(3,item.logo);
                        status.bindString(4,item.group);
                        status.bindString(5,item.url);
                        status.executeUpdateDelete();
                    }
                });
            }finally{stmt.close();status.close();}
            db.setTransactionSuccessful();
        }catch(IllegalArgumentException ex){throw new IOException(ex.getMessage(),ex);
        }finally{db.endTransaction();}
        return new ImportResult(scanned[0],inserted[0],count(db));
    }
    private static int count(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM sources",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public synchronized int count(){return count(getReadableDatabase());}
    /** Local-only source data may contain sensitive credentials: wipe on user request. */
    public synchronized void clear(){getWritableDatabase().delete("sources",null,null);}
    private static CatalogStore.Item item(Cursor c){
        return new CatalogStore.Item(c.getString(0),c.getString(1),c.getString(2),
            c.getString(3),c.getString(4),c.getString(5),
            "",c.getString(6),"",""); // source label in provider
    }
    /**
     * Show one card per title; representative rows are recent so older playlists
     * cannot monopolize the Home. Detailed search reaches all rows.
     */
    public synchronized List<CatalogStore.Item> representatives(int max){
        ArrayList<CatalogStore.Item> out=new ArrayList<>();
        int bound=Math.min(180,Math.max(0,max));
        String sql="SELECT s.name,s.url,s.tvg_id,s.category,s.logo,s.format,s.source_label "
            +"FROM sources s JOIN "
            +"(SELECT identity_key,MAX(id) AS latest FROM sources GROUP BY identity_key "
            +"ORDER BY latest DESC LIMIT "+bound+") x ON s.id=("
            +"SELECT preferred.id FROM sources preferred WHERE preferred.identity_key=x.identity_key "
            +"ORDER BY CASE WHEN preferred.remote_status='available' THEN 0 "
            +"WHEN preferred.remote_status='candidate' THEN 1 ELSE 2 END,"
            +"CASE WHEN preferred.last_good_ms>preferred.last_bad_ms THEN 0 ELSE 1 END,"
            +"preferred.failure_count ASC,preferred.last_good_ms DESC,preferred.id DESC LIMIT 1) "
            +"ORDER BY x.latest DESC";
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){
            while(c.moveToNext())out.add(item(c));
        }
        return out;
    }
    public synchronized List<CatalogStore.Item> search(String phrase,int limit){
        ArrayList<CatalogStore.Item> out=new ArrayList<>();
        String text=phrase==null?"":phrase.trim();
        if(text.length()<2)return out;
        text=text.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        String sql="SELECT s.name,s.url,s.tvg_id,s.category,s.logo,s.format,s.source_label "
            +"FROM sources s JOIN (SELECT identity_key,MAX(id) AS latest FROM sources "
            +"WHERE name LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\' "
            +"GROUP BY identity_key ORDER BY latest DESC LIMIT ?) x ON s.id=("
            +"SELECT preferred.id FROM sources preferred WHERE preferred.identity_key=x.identity_key "
            +"ORDER BY CASE WHEN preferred.remote_status='available' THEN 0 "
            +"WHEN preferred.remote_status='candidate' THEN 1 ELSE 2 END,"
            +"CASE WHEN preferred.last_good_ms>preferred.last_bad_ms THEN 0 ELSE 1 END,"
            +"preferred.failure_count ASC,preferred.last_good_ms DESC,preferred.id DESC LIMIT 1) "
            +"ORDER BY x.latest DESC";
        String pattern="%"+text+"%";
        try(Cursor c=getReadableDatabase().rawQuery(sql,
            new String[]{pattern,pattern,Integer.toString(Math.min(100,Math.max(1,limit)))})){
            while(c.moveToNext())out.add(item(c));
        }
        return out;
    }
    public synchronized List<String> alternatives(CatalogStore.Item selected,int limit){
        ArrayList<String> out=new ArrayList<>();
        String key=SourceIdentity.key(selected.name,selected.group,
            selected.tvgId,selected.format,selected.url);
        String sql="SELECT url FROM sources WHERE identity_key=? "
            +"ORDER BY (CASE WHEN last_good_ms>last_bad_ms THEN 0 ELSE 1 END),"
            +"CASE WHEN remote_status='available' THEN 0 "
            +"WHEN remote_status='candidate' THEN 1 ELSE 2 END,"
            +"failure_count ASC,last_good_ms DESC,id DESC LIMIT ?";
        try(Cursor c=getReadableDatabase().rawQuery(sql,
            new String[]{key,Integer.toString(Math.max(1,Math.min(15,limit)))})){
            while(c.moveToNext())out.add(c.getString(0));
        }
        return out;
    }
    public synchronized String lastWorking(CatalogStore.Item selected){
        String key=SourceIdentity.key(selected.name,selected.group,
            selected.tvgId,selected.format,selected.url);
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT url FROM sources WHERE identity_key=? AND last_good_ms>last_bad_ms "
            +"AND last_good_ms>0 ORDER BY last_good_ms DESC LIMIT 1",
            new String[]{key})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    public synchronized String remoteStatus(String url){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT remote_status FROM sources WHERE url=? LIMIT 1",new String[]{url})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public synchronized boolean contains(String url){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT 1 FROM sources WHERE url=? LIMIT 1",new String[]{url})){
            return c.moveToFirst();
        }
    }
    public synchronized void recordPlayback(String url,boolean success){
        if(url==null)return;
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(success?"last_good_ms":"last_bad_ms",now);
        if(success)values.put("failure_count",0);
        db.update("sources",values,"url=?",new String[]{url});
        if(!success)db.execSQL(
            "UPDATE sources SET failure_count=MIN(failure_count+1,100) WHERE url=?",
            new Object[]{url});
    }
}
