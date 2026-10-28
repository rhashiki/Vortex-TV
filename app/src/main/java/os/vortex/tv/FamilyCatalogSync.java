package os.vortex.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Resumable private catalog delivery. Only the device's one-time family code
 * can receive source URLs. Neither playlists nor credentials are baked into the APK.
 * Failure keeps the last completed SQLite batch and resumes by server cursor.
 */
public final class FamilyCatalogSync {
    private FamilyCatalogSync(){}
    private static final String ENDPOINT=
        "https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-family-catalog";
    private static final int MAX_PAGE_BYTES=1500000;
    private static final int MAX_PAGES=100;
    public static final class Result {
        public final int scanned,added,pages,total;
        public final boolean completed;
        Result(int scanned,int added,int pages,int total,boolean completed){
            this.scanned=scanned;this.added=added;this.pages=pages;this.total=total;
            this.completed=completed;
        }
    }
    private static final class Limit extends FilterInputStream{
        int remaining;
        Limit(InputStream source,int limit){super(source);remaining=limit;}
        @Override public int read()throws IOException{
            if(remaining<=0)throw new IOException("Página do catálogo excede limite");
            int n=super.read();if(n>=0)remaining--;return n;
        }
        @Override public int read(byte[] buffer,int offset,int length)throws IOException{
            if(remaining<=0)throw new IOException("Página do catálogo excede limite");
            int n=super.read(buffer,offset,Math.min(length,remaining));
            if(n>0)remaining-=n;return n;
        }
    }
    public interface ProgressListener {
        void onPage(int pages,int scanned,int added,int total);
    }
    public static Result sync(Context context,CatalogStore catalog,
                              SharedPreferences preferences,String pairingCode)
        throws Exception {
        return sync(context,catalog,preferences,pairingCode,null);
    }
    public static Result sync(Context context,CatalogStore catalog,
                              SharedPreferences preferences,String pairingCode,
                              ProgressListener listener)
        throws Exception {
        if(pairingCode==null || !pairingCode.matches("[A-F0-9]{20}"))
            throw new IOException("Código Vortex Família inválido");
        long cursor=preferences.getLong("family_cursor_v1",0L);
        int scanned=0,added=0,pages=0,total=catalog.importedCount();
        boolean completed=false;
        for(int page=0;page<MAX_PAGES;page++){
            HttpURLConnection connection=(HttpURLConnection)
                new URL(ENDPOINT+"?after="+cursor).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(16000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept","application/x-mpegURL");
            connection.setRequestProperty("x-vortex-family-key",pairingCode);
            try{
                int status=connection.getResponseCode();
                if(status==401||status==403)
                    throw new IOException("Acesso à família não autorizado; verifique o código");
                if(status!=200)
                    throw new IOException("Servidor temporariamente indisponível (HTTP "+status+")");
                long size=connection.getContentLengthLong();
                if(size>MAX_PAGE_BYTES)throw new IOException("Página do catálogo excede limite");
                String next=connection.getHeaderField("X-Vortex-Next-Cursor");
                String more=connection.getHeaderField("X-Vortex-Has-More");
                if(next==null||!next.matches("[0-9]{1,18}")
                    ||(!"true".equals(more)&&!"false".equals(more)))
                    throw new IOException("Resposta do catálogo incompleta");
                long nextCursor=Long.parseLong(next);
                if(nextCursor<cursor||("true".equals(more)&&nextCursor==cursor))
                    throw new IOException("Cursor inválido do catálogo");
                ImportedSourceStore.ImportResult result;
                try(InputStream source=new Limit(connection.getInputStream(),MAX_PAGE_BYTES);
                    InputStreamReader reader=new InputStreamReader(source,"UTF-8")){
                    result=catalog.importPlaylist(reader,"Vortex Família");
                }
                scanned+=result.scanned;
                added+=result.added;
                total=result.total;
                pages++;
                if(listener!=null)listener.onPage(pages,scanned,added,total);
                // Only acknowledge a cursor AFTER the corresponding SQLite transaction commits.
                cursor=nextCursor;
                preferences.edit().putLong("family_cursor_v1",cursor).apply();
                if("false".equals(more)){
                    completed=true;
                    preferences.edit().putLong("family_last_sync_v1",
                        System.currentTimeMillis()).apply();
                    break;
                }
            }finally{connection.disconnect();}
        }
        return new Result(scanned,added,pages,total,completed);
    }
}
