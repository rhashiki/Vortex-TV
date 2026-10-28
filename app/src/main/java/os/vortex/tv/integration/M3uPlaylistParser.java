package os.vortex.tv.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** No external dependencies; Android 7.1/API25 compatible. */
public final class M3uPlaylistParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*\"([^\"]*)\"");
    private static final int MAX_CHANNELS = 60000;

    public static final class Channel {
        public final String name, url, tvgId, group, logo, format;
        public final boolean verified;
        Channel(String name, String url, String tvgId, String group, String logo, String format) {
            this.name=name; this.url=url; this.tvgId=tvgId; this.group=group;
            this.logo=logo; this.format=format; this.verified=false;
        }
    }

    public List<Channel> parse(Reader input) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        String header = reader.readLine();
        if (header == null || !header.replace("\uFEFF", "").trim().startsWith("#EXTM3U"))
            throw new IOException("Playlist nao e M3U estendido");
        ArrayList<Channel> channels = new ArrayList<Channel>();
        Map<String,String> attrs = Collections.emptyMap();
        String title = "";
        boolean pending = false;
        String line;
        while ((line=reader.readLine()) != null) {
            line=line.trim();
            if (line.length()==0) continue;
            if (line.startsWith("#EXTINF:")) {
                attrs = new HashMap<String,String>();
                int comma=-1;
                boolean quoted=false;
                for (int i=0;i<line.length();i++) {
                    char c=line.charAt(i);
                    if (c=='"') quoted=!quoted;
                    else if (c==',' && !quoted) {comma=i;break;}
                }
                String head = comma>=0 ? line.substring(0,comma) : line;
                Matcher matcher = ATTRIBUTE.matcher(head);
                while (matcher.find()) attrs.put(matcher.group(1),matcher.group(2));
                title = comma>=0 ? line.substring(comma+1).trim() : attrs.get("tvg-name");
                String canonical=attrs.get("tvg-name");
                if(canonical!=null&&!canonical.trim().isEmpty())title=canonical.trim();
                title=cleanDisplayName(title);
                if (title==null || title.isEmpty()) title="Canal sem nome";
                pending=true;
                continue;
            }
            if (line.charAt(0)=='#') continue;
            if (!pending) continue;
            pending=false;
            if (!isHttpUrl(line)) continue;
            if (channels.size() >= MAX_CHANNELS) throw new IOException("Playlist excede limite");
            channels.add(new Channel(title,line,attr(attrs,"tvg-id"),attr(attrs,"group-title"),
                    attr(attrs,"tvg-logo"),kind(line)));
        }
        return channels;
    }
    /**
     * Strip IPTV quality/group labels, preserving the broadcaster's actual name.
     * Prefer tvg-name over ad-hoc release names in EXTINF descriptions.
     */
    static String cleanDisplayName(String name) {
        if(name==null)return "";
        String cleaned=name.trim().replaceAll("\\s+"," ");
        cleaned=cleaned.replaceAll("(?i)^(?:BR|BRA|BRAZIL|BRASIL)\\s*[-:|]\\s*","");
        cleaned=cleaned.replaceAll("(?i)\\s*(?:\\[|\\()(?:HD|FHD|SD|4K|UHD|1080P|720P|H265)(?:\\]|\\))\\s*$","");
        cleaned=cleaned.replaceAll("(?i)\\s+[-|:]\\s*(?:HD|FHD|SD|4K|UHD|1080P|720P)\\s*$","");
        cleaned=cleaned.replaceAll("\\s+"," ").trim();
        return cleaned.length()>110?cleaned.substring(0,110).trim():cleaned;
    }
    private static String attr(Map<String,String> attrs,String key) {String v=attrs.get(key);return v==null?"":v;}
    public static boolean isHttpUrl(String str) {
        try {
            URI u=new URI(str);
            String scheme=u.getScheme();
            return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))
                && u.getHost()!=null && !u.getHost().isEmpty();
        } catch(Exception e) { return false; }
    }
    public static String kind(String url) {
        String path;
        try {path = new URI(url).getPath().toLowerCase(java.util.Locale.ROOT);} catch(Exception e){return "auto";}
        if (path.endsWith(".m3u8")) return "hls";
        if (path.endsWith(".mpd")) return "dash";
        if (path.endsWith(".mp4")||path.endsWith(".webm")||path.endsWith(".mkv")) return "file";
        return "auto";
    }
}
