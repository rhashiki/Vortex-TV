from pathlib import Path
import json, unittest
ROOT=Path(__file__).resolve().parents[1]
class AnimationRequests100(unittest.TestCase):
    def test_catalog_has_exact_user_requests(self):
        path=ROOT/"catalog/animation/series_requested_100.json"
        obj=json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(obj["schema"],1)
        rows=obj["series"]
        self.assertEqual(len(rows),100)
        self.assertEqual(len({r["show_key"] for r in rows}),100)
        self.assertEqual(len({(r["display_title"],r["original_year"]) for r in rows}),100)
        self.assertTrue(any(r["display_title"]=="Os Simpsons" and r["original_year"]==1989 for r in rows))
        self.assertEqual(sorted(r["original_year"] for r in rows if r["display_title"]=="As Tartarugas Ninja"),[2003,2012])
    def test_bot_uses_db_titles_not_hardcoded(self):
        code=(ROOT/"supabase/functions/vortex-animation-series-update/index.ts").read_text()
        self.assertIn("vortex_animation_target_lookup",code)
        self.assertIn("search/shows?q=",code)
        self.assertIn("selectShow(results,target)",code)
        self.assertNotIn("const catalog: Record<string,string>",code)
        self.assertIn("vortex_animation_import_tvmaze",code)
        self.assertIn("x-vortex-scheduler",code)
if __name__=="__main__":unittest.main()
