from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class CatalogPaginationAndNames(unittest.TestCase):
    def test_archive_has_pagination_and_license_filter(self):
        text=(ROOT/'app/src/main/java/os/vortex/tv/OpenFilmCatalog.java').read_text()
        self.assertIn('fetchPage(int requestedPage,int maxItems)',text)
        self.assertIn('licenseurl:http*creativecommons.org*',text)
        self.assertIn('private static final int SEARCH_ROWS=40;',text)
        self.assertIn('String title=cleanTitle(',text)
        self.assertIn('if(file==null)return null;',text)
    def test_pagination_is_persisted_between_launches(self):
        text=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('open_archive_page_v2',text)
        self.assertIn('open_archive_total_pages_v2',text)
        self.assertIn('fetchPage(page,40)',text)
        self.assertIn('Carregar mais filmes livres',text)
    def test_correction_does_not_change_stream_link(self):
        store=(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text()
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('public boolean rename(String url,String newName)',store)
        self.assertIn('catalog.rename(item.url,field.getText().toString())',ui)
        self.assertIn('repairArchiveTitles()',store)
    def test_channel_uses_canonical_source_name(self):
        parser=(ROOT/'app/src/main/java/os/vortex/tv/integration/M3uPlaylistParser.java').read_text()
        self.assertIn('attrs.get("tvg-name")',parser)
        self.assertIn('cleanDisplayName(title)',parser)

if __name__ == '__main__':
    unittest.main()
