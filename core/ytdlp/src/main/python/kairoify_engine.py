# Copyright (c) 2026 WebWalker3D. All Rights Reserved.
#
# The contents of this file are subject to the Common Public Attribution License
# Version 1.0 (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
# https://github.com/WebWalker3D/Kairoify/blob/main/LICENSE. The License is based on
# the Mozilla Public License Version 1.1 but Sections 14 and 15 have been added to
# cover use of software over a computer network and provide for limited attribution
# for the Original Developer. In addition, Exhibit A has been modified to be
# consistent with Exhibit B.
#
# Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
# WARRANTY OF ANY KIND, either express or implied. See the License for the specific
# language governing rights and limitations under the License.
#
# The Original Code is Kairoify. The Initial Developer of the Original Code is
# WebWalker3D. Attribution information: see NOTICE.md.

"""Kairoify's music engine: yt-dlp and ytmusicapi behind a few JSON-in, JSON-out functions called from Kotlin.

Every public function returns JSON text. Failures come back as {"error": {"type": ..., "message": ...}} so the
Kotlin side can say why in plain words; nothing here raises into Java.
"""

import json
import os
import sys
import tempfile

FORMAT = "bestaudio[ext=m4a][acodec^=mp4a]/bestaudio[acodec=opus]/bestaudio[ext=mp3]/bestaudio"

_fast = None
_full = None
_flat = None
_ytm = None


def _ok(value):
    return json.dumps(value, default=str)


def _fail(error):
    return json.dumps({"error": {"type": type(error).__name__, "message": str(error)}})


def _guard(fn):
    def wrapper(*args):
        try:
            return fn(*args)
        except Exception as e:  # every failure goes back to Kotlin as data
            return _fail(e)

    wrapper.__name__ = fn.__name__
    return wrapper


class NotStarted(Exception):
    pass


def _import_ytdlp(update_zip):
    """Loads yt-dlp, preferring a downloaded update; falls back to the bundled copy if the update won't import."""
    if update_zip and os.path.isfile(update_zip):
        sys.path.insert(0, update_zip)
        try:
            import yt_dlp

            # Python skips a path entry it can't read and finds the bundled copy instead, so check where it came from.
            if not os.path.abspath(yt_dlp.__file__).startswith(os.path.abspath(update_zip) + os.sep):
                raise ImportError("The downloaded engine isn't a readable yt-dlp release")
            return yt_dlp, True, None
        except Exception as e:
            if update_zip in sys.path:
                sys.path.remove(update_zip)
            for name in [m for m in sys.modules if m.startswith("yt_dlp")]:
                del sys.modules[name]
            import yt_dlp

            return yt_dlp, False, str(e)
    import yt_dlp

    return yt_dlp, False, None


@_guard
def start(cache_dir, qjs_path, update_zip):
    global _fast, _full, _flat
    os.makedirs(cache_dir, exist_ok=True)
    os.environ["TMPDIR"] = cache_dir
    tempfile.tempdir = cache_dir
    yt_dlp, updated, update_error = _import_ytdlp(update_zip)
    base = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "format": FORMAT,
        "cachedir": os.path.join(cache_dir, "ytdlp"),
    }
    _fast = yt_dlp.YoutubeDL({**base, "extractor_args": {"youtube": {"player_client": ["visionos"]}}})
    _full = yt_dlp.YoutubeDL({**base, "js_runtimes": {"quickjs": {"path": qjs_path}}})
    _flat = yt_dlp.YoutubeDL({"quiet": True, "no_warnings": True, "skip_download": True, "extract_flat": True})
    return _ok({"version": yt_dlp.version.__version__, "updated": updated, "update_error": update_error})


def _need(engine):
    if engine is None:
        raise NotStarted("The music engine hasn't started")
    return engine


@_guard
def resolve(url, mode):
    ydl = _need(_fast if mode == "fast" else _full)
    info = ydl.extract_info(url, download=False)
    return _ok(
        {
            "url": info.get("url"),
            "protocol": info.get("protocol"),
            "ext": info.get("ext"),
            "acodec": info.get("acodec"),
            "http_headers": info.get("http_headers") or {},
            "duration": info.get("duration"),
        }
    )


@_guard
def flat_search(query_url):
    info = _need(_flat).extract_info(query_url, download=False)
    return _ok({"entries": list(info.get("entries") or [])})


def _music():
    global _ytm
    _need(_fast)
    if _ytm is None:
        from ytmusicapi import YTMusic

        _ytm = YTMusic()
    return _ytm


@_guard
def ytm_search(query, limit):
    return _ok({"results": _music().search(query, filter="songs", limit=int(limit))})


@_guard
def ytm_radio(video_id, limit):
    playlist = _music().get_watch_playlist(video_id, radio=True, limit=int(limit))
    return _ok({"tracks": playlist.get("tracks") or []})
