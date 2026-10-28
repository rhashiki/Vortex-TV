from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]

class Wiring(unittest.TestCase):
    def test_assets(self):
        import json
        p=ROOT/'catalog/youtube/seed_referencias.json'
        self.assertGreaterEqual(len(json.loads(p.read_text())['items']),49)
        self.assertEqual(len({x['video_id'] for x in json.loads(p.read_text())['items']}),len(json.loads(p.read_text())['items']))
    def test_yt_activity_not_extracting_media(self):
        java=(ROOT/'app/YouTubePlaybackActivity.java').read_text()
        self.assertIn('youtube-nocookie.com/embed/',java)
        self.assertIn('Abrir no YouTube',java)
        self.assertIn('KEYCODE_DPAD_UP',java)
        self.assertIn('KEYCODE_DPAD_DOWN',java)
        self.assertNotIn('yt-dlp',java)
    def test_adapter(self):
        java=(ROOT/'app/YoutubeCatalogBridge.java').read_text()
        self.assertIn('https://raw.githubusercontent.com/rhashiki/Vortex-TV/main/catalog/youtube/index.json',java)
        self.assertIn('youtube-live',java)
        self.assertIn('youtube-movie',java)
if __name__=='__main__': unittest.main()