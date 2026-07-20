# Build script for Tournament Manager plugin
# Use local JDK 25 if JAVA_HOME is not already set (e.g., in CI)
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Users\Matthew\OneDrive\Desktop\mc-plugins\jdk25"
}

$mvnArgs = @("clean", "package")

# Suppress download progress bars in CI (GitHub Actions)
if ($env:CI) {
    $mvnArgs += "--no-transfer-progress"
}

# Force delete target folder locally to avoid locked file issues
if (-not $env:CI) {
    Remove-Item -Path "target" -Recurse -Force -ErrorAction SilentlyContinue
}

mvn $mvnArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nBuild successful!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nBuild failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
