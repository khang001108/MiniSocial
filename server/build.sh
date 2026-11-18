#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
javac -encoding UTF-8 -d build/classes src/minisocial/*.java
jar cfe build/minisocial-server.jar minisocial.Server -C build/classes .
echo "Built server/build/minisocial-server.jar"
