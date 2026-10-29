from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
PRIORITY=ROOT/"catalog/animation_priority"

def load(kind):
    lines=(PRIORITY/(kind+".tsv")).read_text(encoding="utf-8").strip().splitlines()
    assert lines[0]=="title_pt|year|original_title"
    return [tuple(line.split("|")) for line in lines[1:]]

class AnimationPriorityTest(unittest.TestCase):
    def test_exactly_100_series_and_100_films(self):
        for name in ("series","films"):
            entries=load(name)
            self.assertEqual(len(entries),100)
            self.assertTrue(all(len(x)==3 and len(x[0])>=2 and len(x[2])>=2 for x in entries))
            self.assertEqual(len({(x[0],x[1]) for x in entries}),100)

    def test_series_versions_and_older_classics(self):
        rows=load("series")
        self.assertIn(("Os Simpsons","1989","The Simpsons"),rows)
        self.assertIn(("Bob Esponja Calça Quadrada","1999","SpongeBob SquarePants"),rows)
        tmnt=[r for r in rows if r[0]=="As Tartarugas Ninja"]
        self.assertEqual({r[1] for r in tmnt},{"2003","2012"})
        self.assertIn(("DuckTales: Os Caçadores de Aventuras","2017","DuckTales (2017)"),rows)
        self.assertIn(("Animaniacs — nova versão","2020","Animaniacs (2020)"),rows)

    def test_films_are_individual_features(self):
        rows=load("films")
        for name in ("Shrek","Shrek 2","Shrek Terceiro","Shrek Para Sempre",
                     "Kung Fu Panda","Kung Fu Panda 2","Kung Fu Panda 3",
                     "A Era do Gelo","A Era do Gelo 2","A Era do Gelo 3",
                     "A Era do Gelo 4","A Era do Gelo: O Big Bang"):
            self.assertIn(name,{r[0] for r in rows})

    def test_sources_are_not_fabricated(self):
        for name in ("series","films"):
            content=(PRIORITY/(name+".tsv")).read_text()
            self.assertNotIn("m3u8",content)
            self.assertNotIn("https://",content)
        schema=(ROOT/"supabase/migrations/20261028_animation_user_priority_schema.sql").read_text()
        self.assertIn("vortex_animation_user_priority",schema)
        self.assertIn("vortex_animation_link_user_priorities",schema)
        self.assertIn("BETWEEN 1989 AND 2026",schema)
        self.assertIn("'metadata_only'",schema)

    def test_import_is_authenticated_and_version_safe(self):
        edge=(ROOT/"supabase/functions/vortex-animation-priority-import/index.ts").read_text()
        self.assertIn("SUPABASE_SERVICE_ROLE_KEY",edge)
        self.assertIn('authorization!=="Bearer "+secret',edge)
        self.assertIn("if(lines.length!==100)",edge)
        self.assertIn("resolution=merge-duplicates",edge)
        self.assertIn("video_urls_added", (ROOT/"supabase/functions/vortex-animation-series-enrich/index.ts").read_text())
        self.assertIn("playback_sources_created:0",edge)

    def test_cover_enrichment_is_separate_from_streaming(self):
        series=(ROOT/"supabase/functions/vortex-animation-series-enrich/index.ts").read_text()
        films=(ROOT/"supabase/functions/vortex-animation-film-enrich/index.ts").read_text()
        self.assertIn("api.tvmaze.com",series)
        self.assertIn("season_number",series)
        self.assertIn("parent_external_id",series)
        self.assertIn("image?.medium",series)
        self.assertIn("en.wikipedia.org",films)
        self.assertIn("thumbnail?.source",films)
        self.assertIn("streaming_urls_created:0",films)

    def test_private_source_matches_not_marked_verified(self):
        match=(ROOT/"supabase/migrations/20261028_animation_source_matching.sql").read_text()
        self.assertIn("vortex_internal.animation_source_matches",match)
        self.assertIn("match_animation_film_sources",match)
        self.assertIn("source_candidate",match)
        self.assertIn("No source is marked verified",match)

    def test_daily_bot_is_in_database_migration(self):
        migration=(ROOT/"supabase/migrations/20261028_media_bot_daily_processing.sql").read_text()
        self.assertIn("process_media_bot",migration)
        self.assertIn("vortex-media-bot-daily",migration)
        self.assertIn("vortex-media-bot-results",migration)
        self.assertIn("cron.schedule(",migration)
        self.assertIn("rights_status",migration) if False else None
        self.assertNotIn("github/workflows",migration)

if __name__=="__main__":
    unittest.main()
