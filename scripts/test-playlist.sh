#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/parser-tests
javac -encoding UTF-8 -d build/parser-tests app/src/main/java/cn/mint/tv/Playlist.java tests/PlaylistTest.java
java -cp build/parser-tests PlaylistTest app/src/main/assets/fujian-mobile.txt
