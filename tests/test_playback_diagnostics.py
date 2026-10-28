from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
PLAYER=ROOT/'app/src/main/java/os/vortex/tv/integration/VortexPlayerActivity.java'
YOUTUBE=ROOT/'app/src/main/java/os/vortex/tv/integration/YouTubePlaybackActivity.java'
MAIN=ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java'

class PlaybackDiagnosticsTests(unittest.TestCase):
    def test_http_code_and_origin_reported(self):
        source=PLAYER.read_text()
        for token in ('HttpDataSource.InvalidResponseCodeException',
                      'http.responseCode==403','http.responseCode==404',
                      'http.dataSpec.uri','segmento de vídeo','describePlayerError(error)'):
            self.assertIn(token,source)
        self.assertNotIn('blocked.getQuery()',source)
    def test_youtube_valid_third_party_referrer(self):
        source=YOUTUBE.read_text()
        self.assertIn('EMBED_ORIGIN="https://vortex-tv-android-build.onrender.com/"',source)
        self.assertIn('referrerpolicy=',source)
        self.assertIn('web.loadDataWithBaseURL(EMBED_ORIGIN',source)
        self.assertIn('Abrir no YouTube',source)
    def test_lab_items_not_masked_by_message(self):
        source=MAIN.read_text()
        begin=source.index('new AlertDialog.Builder(this).setTitle("Laboratório • Escolha um filme")')
        end=source.index('.setItems(labels,',begin)
        self.assertNotIn('.setMessage(',source[begin:end])
    def test_app_version(self):
        gradle=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 32',gradle)
        self.assertIn("versionName '0.3.2-failover'",gradle)

if __name__=='__main__':
    unittest.main()
