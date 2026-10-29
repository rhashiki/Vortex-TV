from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]

class NativeFirmwareIntegrationTests(unittest.TestCase):
    def code(self,path):
        return (ROOT/path).read_text(encoding="utf-8")

    def test_distinct_application_id_and_activities(self):
        gradle=self.code("app/build.gradle")
        manifest=self.code("app/src/main/AndroidManifest.xml")
        self.assertIn("applicationId 'os.vortex.tv.stream'",gradle)
        self.assertNotIn("applicationId 'os.vortex.tv'",gradle)
        self.assertIn('android:name="os.vortex.tv.MainActivity"',manifest)
        self.assertIn("versionCode 35",gradle)
        self.assertIn("versionName '0.3.5-source-repair'",gradle)

    def test_zero_touch_native_sync(self):
        ui=self.code("app/src/main/java/os/vortex/tv/MainActivity.java")
        self.assertIn("syncNativeCatalog(false);",ui)
        self.assertIn("NativeCatalogConfig.readFamilyAccess(getApplicationContext())",ui)
        self.assertIn("FamilyCatalogSync.sync(",ui)
        self.assertIn("family_last_sync_v1",ui)
        self.assertIn("syncNativeCatalog(true)",ui)
        self.assertNotIn("showFamilyPairing",ui)
        self.assertNotIn("family_pair_code_v1",ui)

    def test_key_provisioned_only_by_oem_image(self):
        config=self.code("app/src/main/java/os/vortex/tv/NativeCatalogConfig.java")
        self.assertIn('"/system/etc/vortex-tv/catalog-access"',config)
        self.assertIn('code.matches("[A-F0-9]{20}")',config)
        self.assertNotIn("SUPABASE_SERVICE_ROLE_KEY",config)
        self.assertNotIn("gen_random_bytes",config)
        sync=self.code("app/src/main/java/os/vortex/tv/FamilyCatalogSync.java")
        self.assertIn('"x-vortex-family-key"',sync)
        self.assertIn('"family_cursor_v1"',sync)
        self.assertIn('"family_last_sync_v1"',sync)
        self.assertIn("catalog.importPlaylist(reader",sync)
        self.assertIn("prefs",self.code("app/src/main/java/os/vortex/tv/CatalogStore.java"))

    def test_existing_local_fallback_retained(self):
        player=self.code("app/src/main/java/os/vortex/tv/integration/VortexPlayerActivity.java")
        self.assertIn("if(tryAlternative())return;",player)
        self.assertIn("recordPlayback(mediaUrl,true)",player)

if __name__=="__main__":unittest.main()
