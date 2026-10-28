package os.vortex.tv;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import os.vortex.tv.integration.M3uPlaylistParser;

/** Synthetic 33,601-row file simulation; no uploaded URLs or credentials in source control. */
public final class M3uStreamingSmokeTest {
    public static void main(String[] args) throws Exception {
        StringBuilder data=new StringBuilder("#EXTM3U\n");
        for(int i=0;i<33601;i++){
            String title="Canal "+(i%300);
            data.append("#EXTINF:-1 tvg-id=\"c").append(i%300)
                .append("\" group-title=\"Canais | Abertos\" tvg-name=\"")
                .append(title).append("\",").append(title).append("\n")
                .append("https://example.test/live/test/").append(i).append(".m3u8\n");
        }
        AtomicInteger total=new AtomicInteger();
        M3uPlaylistParser parser=new M3uPlaylistParser();
        parser.parseToSink(new StringReader(data.toString()),ch->{
            if(!ch.name.startsWith("Canal ") || !"hls".equals(ch.format))
                throw new IllegalStateException("Corrupted stream metadata");
            total.incrementAndGet();
        });
        if(total.get()!=33601)throw new IllegalStateException("Expected 33601, got "+total.get());
        System.out.println("M3u streaming 33601/33601 synthetic entries parsed");
    }
}
