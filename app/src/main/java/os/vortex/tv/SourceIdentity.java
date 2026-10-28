package os.vortex.tv;

import java.text.Normalizer;
import java.net.URI;
import java.util.Locale;

/** One content identity, potentially many playback sources. Never merge unrelated episodes. */
public final class SourceIdentity {
    private SourceIdentity(){}

    private static String normalized(String value){
        String text=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).trim();
        return text.replaceAll("[^A-Z0-9]+"," ").replaceAll("\\s+"," ").trim();
    }

    private static String urlFingerprint(String url){
        try {
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256")
                .digest((url==null?"":url).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(int i=0;i<16;i++)
                out.append(String.format(Locale.ROOT,"%02x",bytes[i]&0xff));
            return out.toString();
        }catch(Exception ignored){
            return Integer.toHexString((url==null?"":url).hashCode());
        }
    }
    static String cleanTitle(String name){
        String value=normalized(name);
        // Remove transport/quality labels, not meaningful words inside movie titles.
        for(int i=0;i<3;i++){
            String next=value
                .replaceAll("(?i)\\s+(?:ALT|ALTERNATIVO|ALTERNATIVA|BACKUP|RESERVA)(?:\\s+[0-9]+)?$","")
                .replaceAll("(?i)\\s+(?:4K|UHD|FULL\\s*HD|FHD|HD|SD|1080P|720P|480P|HEVC|H265|H264)$","")
                .replaceAll("(?i)\\s+(?:LINK|FONTE|OPCAO)\\s*[1-9][0-9]*$","").trim();
            if(next.equals(value))break;
            value=next;
        }
        return value;
    }

    public static String key(String name,String group,String tvgId,String format,String url){
        String title=cleanTitle(name);
        if(title.isEmpty()){
            // Missing titles cannot safely be merged with unrelated media.
            return "url:"+urlFingerprint(url);
        }
        String g=normalized(group);
        String f=format==null?"":format.toLowerCase(Locale.ROOT);
        if(f.startsWith("youtube-"))return "youtube|"+title;
        boolean genericEpisode=title.matches("(?i)^(?:T[0-9]{1,2}\\s*EP\\s*[0-9]{1,3}|S[0-9]{1,2}\\s*E[0-9]{1,3}|EP\\s*[0-9]{1,3})$");
        if(genericEpisode){
            // e.g. T01 EP03 exists in hundreds of completely different shows.
            if(g.equals("SERIADO")||g.equals("SERIE")||g.equals("SERIES")||g.isEmpty())
                return "isolated-episode:"+urlFingerprint(url);
            return "episode|"+g+"|"+title;
        }
        boolean tv=g.contains("CANAIS")||g.contains("TV")||g.contains("AO VIVO")
            ||g.contains("LIVE")||g.contains("NOTICIAS")||g.contains("ESPORTES")
            ||g.startsWith("REDE ");
        // IPTV linear TS URLs are often labelled as format='auto', while
        // backup HLS URLs have format='hls'. Both must resolve to one service.
        if(url!=null&&(url.toLowerCase(Locale.ROOT).matches(".*\\.ts(?:[?].*)?$")
            ||url.toLowerCase(Locale.ROOT).contains("/live/")))tv=true;
        boolean videoOnDemand=g.contains("FILME")||g.contains("SERIE")
            ||g.contains("SERIADO")||g.contains("ON DEMAND")||g.contains("VOD")
            ||g.contains("TEMPORADA")||g.contains("ANIME");
        if(tv&&(!videoOnDemand||g.contains("CANAIS")))return "channel|"+title;
        if(videoOnDemand||"file".equals(f))return "video|"+title;
        if("hls".equals(f)||"dash".equals(f))return "channel|"+title;
        return "video|"+title;
    }
}
