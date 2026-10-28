package os.vortex.tv;

/** Pure JDK smoke checks; runs without Android SDK and never uses live sources. */
public final class SourceIdentitySmokeTest {
    private static void check(boolean yes,String explanation){
        if(!yes)throw new IllegalStateException(explanation);
    }
    public static void main(String[] args){
        String hd=SourceIdentity.key("Globo RJ HD","Rede Globo","Globorj.br","auto","https://a.test/1.ts");
        String backup=SourceIdentity.key("Globo RJ Alternativo","Rede Globo","Globorj.br","hls","https://b.test/2.m3u8");
        check(hd.equals(backup),"HD and backup channel variants must group");
        String first=SourceIdentity.key("T01 EP03","SUPERNATURAL","","file","https://example.test/a.mp4");
        String other=SourceIdentity.key("T01 EP03","GREY'S ANATOMY","","file","https://example.test/b.mp4");
        check(!first.equals(other),"Episodes from distinct series may not group");
        String noShowA=SourceIdentity.key("T01 EP03","Seriado","","file","https://example.test/a.mp4");
        String noShowB=SourceIdentity.key("T01 EP03","Seriado","","file","https://example.test/b.mp4");
        check(!noShowA.equals(noShowB),"Unknown series must stay isolated");
        String fhd=SourceIdentity.key("O Nevoeiro 1080p","Filmes","","file","https://example.test/1.mp4");
        String sd=SourceIdentity.key("O Nevoeiro SD","Filmes","","file","https://example.test/2.mp4");
        check(fhd.equals(sd),"Same movie sources must group");
        check(!SourceIdentity.key("007 - Cassino Royale","Filmes","","file","a").equals(
               SourceIdentity.key("007 - Skyfall","Filmes","","file","b")),"Different films cannot group");
        System.out.println("SourceIdentity 5/5 checks passed");
    }
}
