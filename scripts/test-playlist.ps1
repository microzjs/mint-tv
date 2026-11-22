$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Force build/parser-tests | Out-Null
    $compiler = 'javac'
    $runtime = 'java'
    if ($env:JAVA_HOME) {
        $compiler = Join-Path $env:JAVA_HOME 'bin/javac.exe'
        $runtime = Join-Path $env:JAVA_HOME 'bin/java.exe'
    }
    & $compiler -encoding UTF-8 -d build/parser-tests app/src/main/java/cn/mint/tv/Playlist.java app/src/main/java/cn/mint/tv/NetworkProfile.java tests/PlaylistTest.java tests/NetworkProfileTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Parser test compilation failed' }
    & $runtime -cp build/parser-tests PlaylistTest app/src/main/assets/fujian-mobile.txt
    if ($LASTEXITCODE -ne 0) { throw 'Parser tests failed' }
    & $runtime -cp build/parser-tests NetworkProfileTest
    if ($LASTEXITCODE -ne 0) { throw 'Network profile tests failed' }
} finally { Pop-Location }
