import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT / 'tools'))
from index_youtube import iso_duration_seconds,clean_title, production_year, era, is_movie_candidate, is_region_available, normalize_video, expand_searches

class YoutubeIndexer(unittest.TestCase):
    def test_duration_and_movie_filter(self):
        self.assertEqual(iso_duration_seconds('PT1H30M'), 5400)
        self.assertEqual(iso_duration_seconds('PT59M59S'), 3599)
        self.assertEqual(iso_duration_seconds('P1DT2H'),93600)
        self.assertFalse(is_movie_candidate('Filme completo dublado trailer', 7200))
        self.assertFalse(is_movie_candidate('Filme completo dublado', 1800))
        self.assertTrue(is_movie_candidate('Filme completo dublado', 4200))
    def test_claimed_year_not_upload(self):
        self.assertEqual(production_year('Filme Completo Dublado (2003)'), 2003)
        self.assertIsNone(production_year('Filme completo dublado'))
        self.assertEqual(era(1997),'Clássicos dos anos 90')
        self.assertEqual(era(2008),'Clássicos dos anos 2000')
        self.assertEqual(era(2017),'Clássicos dos anos 2010')
    def test_region_restriction(self):
        self.assertFalse(is_region_available({'regionRestriction':{'blocked':['BR']}}, 'BR'))
        self.assertFalse(is_region_available({'regionRestriction':{'allowed':['US']}}, 'BR'))
        self.assertTrue(is_region_available({}, 'BR'))
    def test_real_live_only(self):
        source={'id':'DUAyQ8yxg38','snippet':{'title':'Canal Brasil AO VIVO','liveBroadcastContent':'live','channelTitle':'ABC','channelId':'UC111'},'status':{'privacyStatus':'public','embeddable':True},'contentDetails':{},'liveStreamingDetails':{'actualStartTime':'2026-01-01T00:00:00Z'}}
        self.assertEqual(normalize_video(source,'live','Notícias',{})['media_type'],'channel')
        source['liveStreamingDetails']['actualEndTime']='2026-01-01T01:00:00Z'
        self.assertIsNone(normalize_video(source,'live','Notícias',{}))
    def test_embeddable_and_length_required(self):
        source={'id':'DUAyQ8yxg38','snippet':{'title':'O Dia da Múmia - Filme Completo Dublado','channelId':'UCK5RRQ3TdwAbbCWrtnXsN3Q','channelTitle':'NetMovies','publishedAt':'2020-05-13T00:00:00Z'},'status':{'privacyStatus':'public','embeddable':True},'contentDetails':{'duration':'PT1H22M','regionRestriction':{'blocked':['US']}}}
        official={'UCK5RRQ3TdwAbbCWrtnXsN3Q':{'name':'NetMovies'}}
        self.assertEqual(normalize_video(source,'movie','Terror',official)['rights_status'],'CANAL_OFICIAL_REFERENCIADO')
        source['contentDetails']['duration']='PT10M'
        self.assertIsNone(normalize_video(source,'movie','Terror',official))
        source['contentDetails']['duration']='PT90M';source['status']['embeddable']=False
        self.assertIsNone(normalize_video(source,'movie','Terror',official))
    def test_queries_include_decades(self):
        conf=json.loads((ROOT/'catalog/youtube/search_plan.json').read_text())
        self.assertTrue(any('anos 90' in x['q'] for x in conf['movie_queries']))
        self.assertTrue(any('anos 2000' in x['q'] for x in conf['movie_queries']))
        self.assertTrue(any('anos 2010' in x['q'] for x in conf['movie_queries']))
        self.assertGreaterEqual(len(expand_searches(conf)),40)

if __name__=='__main__':unittest.main()