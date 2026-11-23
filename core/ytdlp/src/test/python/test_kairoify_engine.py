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

"""Tests for how kairoify_engine picks between a downloaded yt-dlp and the bundled one. Needs no real yt-dlp:
small fake packages stand in for both. Run: python3 -I -m unittest discover -s core/ytdlp/src/test/python"""

import importlib
import os
import shutil
import sys
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "main", "python"))

import kairoify_engine as engine  # noqa: E402


def _purge():
    for name in [m for m in sys.modules if m.startswith("yt_dlp")]:
        del sys.modules[name]


def _fake_package(root, version):
    pkg = os.path.join(root, "yt_dlp")
    os.makedirs(pkg)
    with open(os.path.join(pkg, "__init__.py"), "w") as f:
        f.write("")
    with open(os.path.join(pkg, "version.py"), "w") as f:
        f.write(f"__version__ = {version!r}\n")


def _fake_zipapp(path, version):
    """Like yt-dlp's release file: a shebang line followed by a zip."""
    with open(path, "wb") as f:
        f.write(b"#!/usr/bin/env python3\n")
        with zipfile.ZipFile(f, "w") as z:
            z.writestr("yt_dlp/__init__.py", "")
            z.writestr("yt_dlp/version.py", f"__version__ = {version!r}\n")


class ImportUpdateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.bundled = os.path.join(self.tmp, "bundled")
        _fake_package(self.bundled, "bundled")
        self.saved_path = list(sys.path)
        sys.path.insert(1, self.bundled)
        _purge()

    def tearDown(self):
        sys.path[:] = self.saved_path
        _purge()
        shutil.rmtree(self.tmp)

    def version(self):
        return importlib.import_module("yt_dlp.version").__version__

    def test_no_update_uses_bundled(self):
        _, updated, error = engine._import_ytdlp(None)
        self.assertFalse(updated)
        self.assertIsNone(error)
        self.assertEqual("bundled", self.version())

    def test_valid_update_is_used(self):
        update = os.path.join(self.tmp, "yt-dlp-2099.1.1.zip")
        _fake_zipapp(update, "2099.1.1")
        _, updated, error = engine._import_ytdlp(update)
        self.assertTrue(updated)
        self.assertIsNone(error)
        self.assertEqual("2099.1.1", self.version())

    def test_broken_update_falls_back_and_says_so(self):
        update = os.path.join(self.tmp, "yt-dlp-2099.1.1.zip")
        with open(update, "w") as f:
            f.write("not-a-zip\n")
        _, updated, error = engine._import_ytdlp(update)
        self.assertFalse(updated)
        self.assertIsNotNone(error)
        self.assertEqual("bundled", self.version())
        self.assertNotIn(update, sys.path)


if __name__ == "__main__":
    unittest.main()
