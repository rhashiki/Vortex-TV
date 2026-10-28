from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class CatalogUpgrade(unittest.TestCase):
    def test_actual_poster_loader(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('Glide.with(this).load(url)',ui)
        self.assertIn('ImageView cover',ui)
    def test_vertical_channels(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('renderLiveGuide()',ui)
        self.assertIn('addGuideRow(',ui)
        self.assertIn('body.addView(line,params);',ui)
    def test_open_movie_api(self):
        code=(ROOT/'app/src/main/java/os/vortex/tv/OpenFilmCatalog.java').read_text()
        self.assertIn('advancedsearch.php',code)
        self.assertIn('creativecommons.org',code)
        self.assertIn('archive.org/metadata/',code.replace('BASE+"/metadata/"','archive.org/metadata/'))
    def test_genres_provider_and_seed(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('Ficção científica',ui)
        self.assertIn('"LGBT"',ui)
        self.assertIn('rowOf("Origem: "',ui)
    def test_media3_compat(self):
        gradle=(ROOT/'app/build.gradle').read_text()
        self.assertIn('compileSdk 36',gradle)
        self.assertIn('com.github.bumptech.glide:glide:4.16.0',gradle)
if __name__=='__main__':unittest.main()
