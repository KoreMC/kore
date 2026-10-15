#!/bin/sh
# Runs whichever Kore jar is beside this script, as its name changes with the version: the newest, if there are a few.
cd "$(dirname "$0")" || exit 1
jar=$(ls kore-*.jar 2>/dev/null | sort -V | tail -n 1)
if [ -z "$jar" ]; then
    echo "There's no kore-*.jar here. Download one from https://github.com/KoreMC/kore/releases"
    exit 1
fi
exec java --sun-misc-unsafe-memory-access=allow -jar "$jar"
