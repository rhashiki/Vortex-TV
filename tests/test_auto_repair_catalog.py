from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class AutomaticCatalogRepair(unittest.TestCase):
    def code(self,path):return (ROOT/path).read_text(encoding="utf-8")
    def test_server_emits_recovery_status_with_title(self):
        code=self.code("supabase/functions/vortex-family-catalog/index.ts")
        self.assertIn('vortex-status=',code)
        self.assertIn('row.catalog_status',code)
        self.assertIn('["available","searching","candidate"]',code)
    def test_client_import_tracks_source_state_without_renaming_titles(self):
        parser=self.code("app/src/main/java/os/vortex/tv/integration/M3uPlaylistParser.java")
        store=self.code("app/src/main/java/os/vortex/tv/ImportedSourceStore.java")
        self.assertIn('attr(attrs,"vortex-status")',parser)
        self.assertIn('public final String remoteStatus',parser)
        self.assertIn("DB_VERSION=2",store)
        self.assertIn("remote_status TEXT NOT NULL DEFAULT 'candidate'",store)
        self.assertIn("status.executeUpdateDelete()",store)
        self.assertIn("public synchronized String remoteStatus(String url)",store)
        self.assertIn("preferred.remote_status='available'",store)
    def test_ui_tag_and_accessibility(self):
        ui=self.code("app/src/main/java/os/vortex/tv/MainActivity.java")
        self.assertIn("Buscando novo servidor",ui)
        self.assertIn("catalog.remoteStatus(i)",ui)
        self.assertIn('boolean searching="searching".equals(remoteState)',ui)
        self.assertIn('poster.setContentDescription(i.name+',ui)
    def test_full_refresh_revisits_earlier_sources(self):
        sync=self.code("app/src/main/java/os/vortex/tv/FamilyCatalogSync.java")
        self.assertIn('putLong("family_cursor_v1",0L)',sync)
        self.assertIn('putLong("family_last_sync_v1"',sync)
    def test_health_worker_secure_and_verifies_segment(self):
        worker=self.code("supabase/functions/vortex-source-monitor/index.ts")
        self.assertIn("vortex_monitor_authorized",worker)
        self.assertIn("vortex_monitor_record",worker)
        self.assertIn('child(lines,"#EXTINF")',worker)
        self.assertIn('"Range":"bytes=0-4095"',worker)
        self.assertIn('redirect:"manual"',worker)
        self.assertIn("newly_available:",worker)
        self.assertNotIn('Deno.env.get("SUPABASE_ANON_KEY")',worker)
    def test_version(self):
        gradle=self.code("app/build.gradle")
        self.assertIn("versionCode 35",gradle)
        self.assertIn("versionName '0.3.5-source-repair'",gradle)
if __name__=="__main__":
    unittest.main()
