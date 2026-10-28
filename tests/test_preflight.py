from pathlib import Path
import json, unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
class Checks(unittest.TestCase):
 def test_bootstrap(self):
  seed=json.loads((ROOT/'app/src/main/assets/seed_catalog.json').read_text())
  self.assertGreaterEqual(len(seed),20)
  self.assertEqual(len(seed),len({row['url'] for row in seed}))
  self.assertTrue(all(row['url'].startswith('https://') for row in seed))
 def test_guide(self):
  metadata=json.loads((ROOT/'app/src/main/assets/guide_only_channels.json').read_text())
  self.assertGreater(len(metadata),50)
  self.assertTrue(all('status' in row for row in metadata))
 def test_android_xml(self):
  ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
  ET.parse(ROOT/'app/src/main/res/values/styles.xml')
 def test_onboarding(self):
  s=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
  self.assertIn('showModeChooser()',s)
  self.assertIn('preferred_mode',s)
 def test_native_player(self):
  s=(ROOT/'app/src/main/java/os/vortex/tv/integration/VortexPlayerActivity.java').read_text()
  for k in ['KEYCODE_DPAD_UP','KEYCODE_DPAD_DOWN','KEYCODE_DPAD_LEFT','KEYCODE_DPAD_RIGHT','ExoPlayer.Builder']:
   self.assertIn(k,s)
if __name__=='__main__':unittest.main()
