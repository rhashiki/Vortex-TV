from pathlib import Path
import json
import unittest

ROOT=Path(__file__).resolve().parents[1]

class DirectPlayUpgrade(unittest.TestCase):
    def test_first_tap_starts_playback(self):
        source=(ROOT/"app/src/main/java/os/vortex/tv/MainActivity.java").read_text()
        self.assertIn("poster.setOnClickListener(v->play(i));",source)
        self.assertIn("poster.setOnLongClickListener(v->{detail(i);return true;});",source)
        self.assertNotIn("poster.setOnClickListener(v->detail(i));",source)
        start=source.index('new AlertDialog.Builder(this).setTitle("Laboratório • Escolha um filme")')
        lab=source[start:source.index('.setNegativeButton("Fechar",null).show();',start)]
        self.assertIn('startActivity(video);',lab)
        self.assertNotIn('.setTitle("Testar: "',lab)
    def test_google_demo_migrated_without_erasing_user_data(self):
        source=(ROOT/"app/src/main/java/os/vortex/tv/CatalogStore.java").read_text()
        self.assertIn('repairLegacyBundledSources()',source)
        self.assertIn('putStringSet(FAV,favorites)',source)
        self.assertIn('if(!old[n].equals(item.url)||!names[n].equals(item.name))continue',source)
        self.assertIn('putBoolean("repair_demo_v3",true)',source)
        bundled=json.loads((ROOT/"app/src/main/assets/seed_catalog.json").read_text())
        for item in bundled:
            self.assertFalse(item["url"].startswith("https://storage.googleapis.com/gtv-videos-bucket/sample/"))
        originals={x["name"]:x for x in bundled}
        self.assertTrue(originals["Big Buck Bunny"]["url"].endswith(".m3u8"))
        self.assertTrue(originals["Tears of Steel"]["url"].endswith(".m3u8"))
        self.assertIn("commons.wikimedia.org",originals["Sintel"]["logo"])
        self.assertIn("commons.wikimedia.org",originals["Big Buck Bunny"]["logo"])
    def test_hls_vod_not_live(self):
        store=(ROOT/"app/src/main/java/os/vortex/tv/CatalogStore.java").read_text()
        self.assertIn('if(group.toLowerCase(java.util.Locale.ROOT).matches(',store)
        self.assertIn('"youtube-series".equals(format)',store)
        self.assertIn('if("hls".equals(format) || "dash".equals(format))return true;',store)
    def test_open_blender_series(self):
        items=json.loads((ROOT/"app/src/main/assets/youtube_seed.json").read_text())["items"]
        self.assertGreaterEqual(len(items),53)
        series=[x for x in items if x.get("media_type")=="episode"]
        self.assertEqual(len(series),3)
        self.assertEqual(len({x["video_id"] for x in series}),3)
        bridge=(ROOT/"app/src/main/java/os/vortex/tv/YoutubeCatalogBridge.java").read_text()
        self.assertIn('"episode".equals(kind)?"youtube-series"',bridge)
    def test_automatic_curated_fallback(self):
        player=(ROOT/"app/src/main/java/os/vortex/tv/integration/VortexPlayerActivity.java").read_text()
        main=(ROOT/"app/src/main/java/os/vortex/tv/MainActivity.java").read_text()
        self.assertIn('if(tryAlternative())return;',player)
        self.assertIn('EXTRA_MEDIA_FALLBACKS_JSON',player)
        self.assertIn('attempted.add(mediaUrl);',player)
        self.assertIn('https://cdn.jwplayer.com/manifests/pZxWPRg4.m3u8',main)
    def test_version(self):
        build=(ROOT/"app/build.gradle").read_text()
        self.assertIn('versionCode 33',build)
        self.assertIn("versionName '0.3.3-native-catalog'",build)

if __name__=="__main__": unittest.main()
