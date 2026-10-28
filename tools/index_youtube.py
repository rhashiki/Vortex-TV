#!/usr/bin/env python3
"""Vortex TV: official YouTube Data API v3 indexer for live TV and full films.

Uses search.list and videos.list only. No page scraping, stream extraction,
DRM bypass, credential harvesting, ad blocking or third-party DEX.

Run: YOUTUBE_API_KEY=... python3 tools/index_youtube.py \
  --config catalog/youtube/search_plan.json --out catalog/youtube/index.json
The key stays in the environment; never write it to Git/GitHub.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import html
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE = "https://www.googleapis.com/youtube/v3/"
TRAILERS = re.compile(r"\b(trailer|teaser|an[aá]lise|review|making of|bastidores|resumo|sinopse|parte\s*\d+|cenas?|compila[cç][aã]o)\b", re.I)
FULL = re.compile(r"(?:filme|longa|movie|full film|full movie|filme completo|document[aá]rio)", re.I)
DUB = re.compile(r"(?:dublado|dublagem|portugu[eê]s|pt[- ]?br|dubbed)", re.I)
YEAR = re.compile(r"(?<!\d)(19(?:9\d)|20(?:0\d|1\d))(?!\d)")
VID = re.compile(r"^[A-Za-z0-9_-]{11}$")


def utc():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def iso_duration_seconds(s: str) -> int:
    """ISO-8601 time portion of YouTube contentDetails.duration."""
    m = re.fullmatch(r"P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?", s or "")
    if not m:
        return 0
    d, h, minutes, sec = (int(part or 0) for part in m.groups())
    return d * 86400 + h * 3600 + minutes * 60 + sec


def clean_title(raw: str) -> str:
    txt = html.unescape(raw or "").replace("\n", " ")
    txt = re.sub(r"\s*[-|:]\s*(?:filme completo(?:\s+dublado)?|full movie|longa completo)(?:\s+\w+){0,2}\s*$", "", txt, flags=re.I)
    txt = re.sub(r"\s+", " ", txt).strip(" |:-")
    return txt[:140]


def production_year(raw: str):
    """Potential year claimed by uploader; never equate with upload date."""
    m = YEAR.search(raw or "")
    return int(m.group(1)) if m else None


def era(year):
    if not year:
        return "Ano não confirmado"
    if 1990 <= year <= 1999:
        return "Clássicos dos anos 90"
    if 2000 <= year <= 2009:
        return "Clássicos dos anos 2000"
    if 2010 <= year <= 2019:
        return "Clássicos dos anos 2010"
    return "Outras épocas"


def is_movie_candidate(title: str, duration_seconds: int, min_minutes: int = 65) -> bool:
    return duration_seconds >= min_minutes * 60 and bool(FULL.search(title)) and not bool(TRAILERS.search(title))


def is_region_available(content: dict, region: str) -> bool:
    restrictions = content.get("regionRestriction") or {}
    allow, block = restrictions.get("allowed"), restrictions.get("blocked") or []
    return (allow is None or region in allow) and region not in block


def thumbnail(snippet: dict, video_id: str):
    thumbs = snippet.get("thumbnails") or {}
    return next((thumbs[name]["url"] for name in ("maxres", "standard", "high", "medium", "default")
                 if thumbs.get(name, {}).get("url")), f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg")


def api_get(key: str, route: str, **params):
    params["key"] = key
    req = Request(BASE + route + "?" + urlencode(params), headers={"Accept": "application/json", "User-Agent": "Vortex-TV-Indexer/1.0"})
    try:
        with urlopen(req, timeout=22) as response:
            raw = response.read(4_000_001)
            if len(raw) > 4_000_000:
                raise ValueError("YouTube API response too large")
            return json.loads(raw.decode("utf-8"))
    except HTTPError as err:
        body = err.read(2000).decode("utf-8", "replace")
        raise RuntimeError(f"YouTube API HTTP {err.code}: {body[:500]}") from err


def normalize_video(video: dict, kind: str, category: str, official_channels: dict, region="BR", min_minutes=65):
    """Metadata-only gate. Embeddable != rights clearance or actual playback."""
    vid = video.get("id") or ""
    if not VID.fullmatch(vid):
        return None
    snippet = video.get("snippet") or {}
    status = video.get("status") or {}
    content = video.get("contentDetails") or {}
    channel_id = snippet.get("channelId") or ""
    published = snippet.get("publishedAt") or ""
    title = html.unescape(snippet.get("title") or "")
    if status.get("privacyStatus") != "public" or status.get("embeddable") is not True:
        return None
    if not is_region_available(content, region):
        return None
    if kind == "live":
        live_state = snippet.get("liveBroadcastContent")
        details = video.get("liveStreamingDetails") or {}
        if live_state != "live" or details.get("actualEndTime"):
            return None
        # A continuous YouTube live video is not necessarily a licensed TV station.
        rights = "CANAL_AUDITAR"
        display_kind = "channel"
    else:
        duration = iso_duration_seconds(content.get("duration") or "")
        if not is_movie_candidate(title, duration, min_minutes):
            return None
        rights = "CANAL_OFICIAL_REFERENCIADO" if channel_id in official_channels else "REVISAR_ORIGEM"
        display_kind = "documentary" if "document" in category.lower() else "movie"
    claimed_year = production_year(title) if kind == "movie" else None
    cleaned = clean_title(title)
    group = era(claimed_year) if kind == "movie" and claimed_year else category
    return {
        "id": "yt:" + vid, "platform": "youtube", "youtube_id": vid,
        "name": cleaned, "raw_title": title,
        "media_type": display_kind, "genre": group,
        "claimed_production_year": claimed_year,
        "year_verified": False,
        "channel_id": channel_id, "provider": snippet.get("channelTitle") or "YouTube",
        "url": "https://www.youtube.com/watch?v=" + vid,
        "poster": thumbnail(snippet, vid),
        "published_at": published,
        "description": html.unescape((snippet.get("description") or "")[:450]),
        "duration_seconds": iso_duration_seconds(content.get("duration") or "") if kind == "movie" else None,
        "embedded_allowed_by_video_status": True,
        "rights_status": rights,
        "playback_status": "METADADOS_VALIDOS_SEM_TESTE_NO_DISPOSITIVO",
        "observed_live_at_scan": kind == "live",
        "last_checked_at": utc(),
        "source": "YouTube Data API v3",
    }


def expand_searches(config: dict):
    """Prioritize 4 verified-distributor channel IDs before generic movie searches."""
    tasks = []
    for c in config["official_movie_channels"]:
        tasks.append({"type": "movie", "category": "Filmes dublados · " + c["name"],
                      "q": "filme completo dublado", "channelId": c["id"]})
        tasks.append({"type": "movie", "category": "Clássicos · " + c["name"],
                      "q": "filme completo", "channelId": c["id"]})
    tasks.extend({"type": "live", **item} for item in config["live_queries"])
    tasks.extend({"type": "movie", **item} for item in config["movie_queries"])
    return tasks


def build_index(key: str, config: dict, max_calls=80, pages_per_query=1):
    if not key or len(key.strip()) < 10:
        raise ValueError("Provide YOUTUBE_API_KEY as an environment variable; never commit credentials")
    tasks = expand_searches(config)
    max_calls = max(1, min(max_calls, config.get("max_search_calls", 80), 100))
    pages_per_query = min(max(1, pages_per_query), 3)
    official = {x["id"]: x for x in config["official_movie_channels"]}
    found = {}
    calls = 0
    task_result = []
    for task in tasks:
        if calls >= max_calls:
            break
        page = None
        ids = []
        for _ in range(pages_per_query):
            if calls >= max_calls:
                break
            args = {"part": "snippet", "q": task["q"], "type": "video",
                    "maxResults": 50, "regionCode": config.get("region", "BR"),
                    "relevanceLanguage": config.get("language", "pt"), "safeSearch": "moderate",
                    "videoEmbeddable": "true"}
            if task["type"] == "live":
                args["eventType"] = "live"
            else:
                args["videoDuration"] = "long"  # API threshold >20 min; our gate is >=65m.
            if task.get("channelId"):
                args["channelId"] = task["channelId"]
            if page:
                args["pageToken"] = page
            result = api_get(key, "search", **args)
            calls += 1
            for it in result.get("items", []):
                value = (it.get("id") or {}).get("videoId") or ""
                if VID.fullmatch(value):
                    ids.append(value)
            page = result.get("nextPageToken")
            if not page:
                break
        # videos.list: one lookup per 50 IDs, much cheaper than checking each video.
        count = 0
        for offset in range(0, len(ids), 50):
            chunk = ids[offset:offset + 50]
            details = api_get(key, "videos", part="snippet,status,contentDetails,liveStreamingDetails", id=",".join(chunk), maxResults=50)
            for video in details.get("items", []):
                item = normalize_video(video, task["type"], task["category"], official,
                                       region=config.get("region", "BR"), min_minutes=config.get("movie_min_minutes", 65))
                if item:
                    # Same video may match several queries: preserve stronger rights confidence.
                    prev = found.get(item["id"])
                    if not prev or (prev["rights_status"] == "REVISAR_ORIGEM" and item["rights_status"] != "REVISAR_ORIGEM"):
                        found[item["id"]] = item
                    count += 1
        task_result.append({"query": task["q"], "type": task["type"],
                            "category": task["category"], "api_candidates": len(ids),
                            "passed_metadata_gates": count})
    videos = sorted(found.values(), key=lambda i: (i["media_type"] != "channel", i["rights_status"] == "REVISAR_ORIGEM", i["name"].casefold()))
    return {"schema": 1, "generated_at": utc(), "origin": "YouTube Data API v3",
            "is_exhaustive": False, "search_calls": calls, "candidate_count": len(videos),
            "description": "Catalog/search index only, not stream verification or authorization to re-host",
            "items": videos, "queries": task_result}


def main(argv=None):
    parser = argparse.ArgumentParser(description="Index YouTube live broadcasts and long movies into structured Vortex TV catalog")
    parser.add_argument("--config", default="catalog/youtube/search_plan.json")
    parser.add_argument("--out", default="catalog/youtube/index.json")
    parser.add_argument("--max-search-calls", type=int, default=70)
    parser.add_argument("--pages-per-query", type=int, default=1)
    parser.add_argument("--check-config", action="store_true")
    args = parser.parse_args(argv)
    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    if args.check_config:
        print(json.dumps({"tasks": len(expand_searches(config)), "official_movie_channels": len(config["official_movie_channels"]),
                          "max_search_calls": min(args.max_search_calls, config["max_search_calls"], 100)}, ensure_ascii=False))
        return 0
    key = os.environ.get("YOUTUBE_API_KEY", "")
    if not key:
        parser.error("YOUTUBE_API_KEY not supplied. Index not refreshed; no fake results were generated.")
    data = build_index(key, config, args.max_search_calls, args.pages_per_query)
    dest = Path(args.out)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp.replace(dest)
    print(json.dumps({"file": str(dest), "videos": data["candidate_count"], "search_calls": data["search_calls"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())