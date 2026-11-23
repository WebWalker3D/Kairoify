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

"""Records search and radio replies from the real services through kairoify_engine, for the yt-dlp sources' tests.

Usage (from the repo root, in a venv with core/ytdlp/requirements.txt installed):
    python -I sources/ytdlp/tools/record_fixtures.py sources/ytdlp/src/test/resources/fixtures

Never records resolve() output: stream URLs carry the requester's IP address.
"""

import json
import os
import sys
import tempfile

here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(here, "..", "..", "..", "core", "ytdlp", "src", "main", "python"))

import kairoify_engine as engine  # noqa: E402


def record(out_dir, name, text):
    reply = json.loads(text)
    if "error" in reply:
        raise SystemExit(f"{name}: {reply['error']}")
    with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
        json.dump(reply, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("wrote", name)


def main(out_dir):
    started = json.loads(engine.start(tempfile.mkdtemp(), "", None))
    if "error" in started:
        raise SystemExit(started["error"])
    record(out_dir, "ytm_search_daft_punk.json", engine.ytm_search("daft punk around the world", 20))
    record(out_dir, "ytm_radio_Jb6gcoR266U.json", engine.ytm_radio("Jb6gcoR266U", 25))
    record(out_dir, "sc_search_daft_punk.json", engine.flat_search("scsearch20:daft punk"))


if __name__ == "__main__":
    main(sys.argv[1])
