# Run from any directory: powershell -NoProfile -File online/scripts/Start-Backend.ps1
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Use-Env.ps1')
$quizBackendPom = Join-Path $PSScriptRoot '../backend/pom.xml'
& mvn -f $quizBackendPom spring-boot:run
exit $LASTEXITCODE
