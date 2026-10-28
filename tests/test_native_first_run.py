from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]

class NativeFirstRunAndSyncTest(unittest.TestCase):
    def test_oem_boot_has_no_mode_prompt(self):
        ui=(ROOT/"app/src/main/java/os/vortex/tv/MainActivity.java").read_text()
        self.assertIn("final boolean nativeInstall=!NativeCatalogConfig.readFamilyAccess(",ui)
        self.assertIn('uiPrefs.edit().putString("preferred_mode",saved).apply()',ui)
        self.assertIn('if(saved.isEmpty()&&!nativeInstall)showModeChooser()',ui)
    def test_oem_header_is_refresh_not_add_sources(self):
        ui=(ROOT/"app/src/main/java/os/vortex/tv/MainActivity.java").read_text()
        self.assertIn('oem?"↻  Catálogo":"⊕  Fontes"',ui)
        self.assertIn("if(oem)syncNativeCatalog(true);else showSources()",ui)
    def test_sync_is_incremental_with_visible_progress(self):
        ui=(ROOT/"app/src/main/java/os/vortex/tv/MainActivity.java").read_text()
        sync=(ROOT/"app/src/main/java/os/vortex/tv/FamilyCatalogSync.java").read_text()
        self.assertIn("nativeCatalogStatus.setText",ui)
        self.assertIn("if(pages%5!=0)return",ui)
        self.assertIn("interface ProgressListener",sync)
        self.assertIn("listener.onPage(pages,scanned,added,total)",sync)
        self.assertIn('preferences.edit().putLong("family_cursor_v1",cursor)',sync)
    def test_version_and_separation(self):
        gradle=(ROOT/"app/build.gradle").read_text()
        self.assertIn("applicationId 'os.vortex.tv.stream'",gradle)
        self.assertIn("versionCode 34",gradle)
        self.assertIn("versionName '0.3.4-native-ux'",gradle)

if __name__=="__main__":
    unittest.main()
