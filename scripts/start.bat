@echo off

title Kore

cd /d "%~dp0"

rem Runs whichever Kore jar is here, as its name changes with the version. Keep only the one you want.
set "jar="
for %%j in (kore-*.jar) do set "jar=%%j"
if not defined jar (
    echo There's no kore-*.jar here. Download one from https://github.com/KoreMC/kore/releases
    pause
    exit /b 1
)

java --sun-misc-unsafe-memory-access=allow -jar "%jar%"
pause
