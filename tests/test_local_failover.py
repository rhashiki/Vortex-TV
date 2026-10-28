from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class LocalMultiSourceFailover(unittest.TestCase):
    def read(self,suffix):
        return (ROOT/suffix).read_text()
    def test_large_private_streaming_import(self):
        parser=self.read("app/src/main/java/os/vortex/tv/integration/M3uPlaylistParser.java")
        store=self.read("app/src/main/java/os/vortex/tv/ImportedSourceStore.java")
        ui=self.read("app/src/main/java/os/vortex/tv/MainActivity.java")
        self.assertIn("parseToSink(Reader input,SourceSink sink)",parser)
        self.assertIn("new M3uPlaylistParser().parseToSink(reader,item->",store)
        self.assertIn("SQLiteOpenHelper",store)
        self.assertIn("DB_NAME=\"vortex_private_sources.db\"",store)
        self.assertIn("MAX_ROWS=120000",store)
        self.assertIn("16_000_000",ui)
        self.assertNotIn("catalog.append(channels)",ui)
    def test_group_by_title_never_merge_generic_episode(self):
        source=self.read("app/src/main/java/os/vortex/tv/SourceIdentity.java")
        self.assertIn("isolated-episode:",source)
        self.assertIn("episode|",source)
        self.assertIn("cleanTitle(String name)",source)
        self.assertIn("ALTERNATIVO",source)
        self.assertNotIn("return \"channel|\"+normalized(tvgId)",source)
    def test_private_url_handling_and_security(self):
        db=self.read("app/src/main/java/os/vortex/tv/ImportedSourceStore.java")
        ui=self.read("app/src/main/java/os/vortex/tv/MainActivity.java")
        manifest=self.read("app/src/main/AndroidManifest.xml")
        self.assertIn('android:usesCleartextTraffic="true"',manifest)
        self.assertIn('isPrivateImportedUrl(i.url)',ui)
        self.assertIn("credenciais sem criptografia",ui)
        self.assertIn("SOMENTE neste dispositivo",ui)
        self.assertNotIn("supabase.co",db)
        self.assertNotIn("github.com",db)
        self.assertIn("source_label",db)
        self.assertIn('getWritableDatabase().delete("sources",null,null)',db)
    def test_retry_on_http_decoder_and_stall(self):
        player=self.read("app/src/main/java/os/vortex/tv/integration/VortexPlayerActivity.java")
        self.assertIn("onPlayerError(PlaybackException error)",player)
        self.assertIn("sourceFailure();",player)
        self.assertIn("if(tryAlternative())return;",player)
        self.assertIn("STARTUP_TIMEOUT_MS=12000",player)
        self.assertIn("REBUFFER_TIMEOUT_MS=18000",player)
        self.assertIn("scheduleTimeout(STARTUP_TIMEOUT_MS)",player)
        self.assertIn("recordPlayback(mediaUrl,true)",player)
        self.assertIn("recordPlayback(mediaUrl,false)",player)
        self.assertIn("loadChannelSource(candidate)",player)
        self.assertIn("loadChannelSource(c)",player)
        self.assertIn("attempted.contains(next)",player)
    def test_index_search_and_bounded_intent(self):
        ui=self.read("app/src/main/java/os/vortex/tv/MainActivity.java")
        store=self.read("app/src/main/java/os/vortex/tv/CatalogStore.java")
        self.assertIn("catalog.searchImported(term,65)",ui)
        self.assertIn("catalog.alternativesFor(i,12)",ui)
        self.assertIn("playable.length()>=120",ui)
        self.assertIn("privateSources.representatives(150)",store)
        self.assertIn("privateSources.alternatives(selected",store)
        self.assertIn("lastWorkingSource(Item item)",store)
        self.assertIn("catalog.lastWorkingSource(i)",ui)
    def test_version(self):
        gradle=self.read("app/build.gradle")
        self.assertIn("versionCode 32",gradle)
        self.assertIn("versionName '0.3.2-failover'",gradle)

if __name__=="__main__":
    unittest.main()
