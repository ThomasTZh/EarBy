# music21 fixture tool notice

This isolated tool uses [music21](https://pypi.org/project/music21/10.5.0/) 10.5.0 (BSD-3-Clause)
pinned in `pyproject.toml`/`uv.lock`. It exists only to generate and independently check the
checked-in theory fixtures under `core/music/src/test/resources/music-fixtures/`; the app never
runs Python, ships no music21 code and adds no runtime dependency. The fixtures are outputs of
this project's own formula tables verified against music21's interval and scale spellings.
