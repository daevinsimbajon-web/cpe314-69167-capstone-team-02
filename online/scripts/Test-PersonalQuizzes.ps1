# Runs browser tests using a disposable PostgreSQL database and separate app ports.
# Build first: mvn -o -f online/backend/pom.xml -DskipTests package
param([string]$TestFilter='', [ValidateSet('personal-quizzes.spec.ts','analytics-overview.spec.ts','retries-analytics.spec.ts','statistics-sharing.spec.ts','email-security.spec.ts','account.spec.ts','professor-fixes.spec.ts','comprehensive-gameplay.spec.ts')][string]$Spec='personal-quizzes.spec.ts', [string]$BackendJar='')
$ErrorActionPreference='Stop'
$quizRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$quizFrontend=Join-Path $quizRoot 'online/frontend'
$quizNode=Join-Path $quizRoot 'online/.tools/node-v22.23.5-win-x64/node.exe'
$quizJar=Join-Path $quizRoot 'online/backend/target/quiz-night-online-1.0-SNAPSHOT.jar'
if($BackendJar){$quizJar=(Resolve-Path -LiteralPath $BackendJar).Path}
$quizOutput=Join-Path $quizFrontend 'test-results/isolated-services'
New-Item -ItemType Directory -Force -Path $quizOutput | Out-Null
$quizContainer='hootly-quiz-e2e-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$quizCaptureProcess=$null;$quizBackendProcess=$null;$quizViteProcess=$null;$quizContainerStarted=$false
$quizEnvKeys=@('DATABASE_URL','POSTGRES_USER','POSTGRES_PASSWORD','ALLOWED_ORIGINS','FRONTEND_URL','HOOTLY_BACKEND_URL','HOOTLY_E2E_BASE_URL','PLAYWRIGHT_CHANNEL','NO_COLOR','FORCE_COLOR','SPRING_PROFILES_ACTIVE','EMAIL_PROVIDER','SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','SMTP_FROM_ADDRESS','SMTP_STARTTLS_ENABLED','SMTP_AUTH_ENABLED','OTP_HMAC_SECRET','HOOTLY_CAPTURE_KEY')
$quizOldEnv=@{};foreach($key in $quizEnvKeys){$quizOldEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
function Wait-QuizEndpoint([string]$Uri,[int]$Seconds=180){
  $until=[DateTime]::UtcNow.AddSeconds($Seconds)
  do {try {$r=Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2;if($r.StatusCode -eq 200){return}} catch {} ; Start-Sleep -Milliseconds 800}while([DateTime]::UtcNow -lt $until)
  throw "Service startup unconfirmed at $Uri. Inspect isolated service logs before retrying."
}
try {
  foreach($port in @(55432,8081,5174,2525,8026)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Test port $port is occupied. No existing service was stopped."}}
  & docker run --rm -d --name $quizContainer --label hootly.test-purpose=personal-quizzes -p 127.0.0.1:55432:5432 -e POSTGRES_USER=hootly_test -e POSTGRES_PASSWORD=isolated_local_test_only -e POSTGRES_DB=hootly_e2e postgres:17-alpine | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Could not start isolated test database'};$quizContainerStarted=$true
  $until=[DateTime]::UtcNow.AddSeconds(30);do{& docker exec $quizContainer pg_isready -U hootly_test -d hootly_e2e *> $null;if($LASTEXITCODE -eq 0){break};Start-Sleep -Milliseconds 800}while([DateTime]::UtcNow -lt $until)
  if($LASTEXITCODE -ne 0){throw 'Test database is not ready'}
  $env:DATABASE_URL='jdbc:postgresql://localhost:55432/hootly_e2e';$env:POSTGRES_USER='hootly_test';$env:POSTGRES_PASSWORD='isolated_local_test_only'
  $env:SPRING_PROFILES_ACTIVE='smtp-capture';$env:EMAIL_PROVIDER='smtp';$env:SMTP_HOST='127.0.0.1';$env:SMTP_PORT='2525';$env:SMTP_USERNAME='';$env:SMTP_PASSWORD='';$env:SMTP_FROM_ADDRESS='hootly@example.test';$env:SMTP_STARTTLS_ENABLED='false';$env:SMTP_AUTH_ENABLED='false'
  $quizBytes=New-Object byte[] 32;$quizRng=[Security.Cryptography.RandomNumberGenerator]::Create();$quizRng.GetBytes($quizBytes);$env:OTP_HMAC_SECRET=[Convert]::ToBase64String($quizBytes);$quizRng.GetBytes($quizBytes);$env:HOOTLY_CAPTURE_KEY=[Convert]::ToBase64String($quizBytes);$quizRng.Dispose()
  $quizCaptureProcess=Start-Process -FilePath $quizNode -ArgumentList @(('"'+(Join-Path $PSScriptRoot 'smtp-capture.mjs')+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $quizOutput 'smtp.log') -RedirectStandardError (Join-Path $quizOutput 'smtp-error.log')
  $env:ALLOWED_ORIGINS='http://127.0.0.1:5174';$env:FRONTEND_URL='http://127.0.0.1:5174'
  $quizIsolatedJar=Join-Path $quizOutput ('backend-under-test-'+[Guid]::NewGuid().ToString('N')+'.jar')
  Copy-Item -LiteralPath $quizJar -Destination $quizIsolatedJar
  $quizJar=$quizIsolatedJar
  $quizBackendProcess=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',('"'+$quizJar+'"'),'--server.port=8081') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $quizOutput 'backend.log') -RedirectStandardError (Join-Path $quizOutput 'backend-error.log')
  Wait-QuizEndpoint 'http://127.0.0.1:8081/api/health'
  $env:HOOTLY_BACKEND_URL='http://127.0.0.1:8081';$env:HOOTLY_E2E_BASE_URL='http://127.0.0.1:5174';$env:PLAYWRIGHT_CHANNEL='msedge'
  Remove-Item Env:NO_COLOR -ErrorAction SilentlyContinue
  $env:FORCE_COLOR='0'
  $quizViteProcess=Start-Process -FilePath $quizNode -ArgumentList @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5174') -WorkingDirectory $quizFrontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $quizOutput 'vite.log') -RedirectStandardError (Join-Path $quizOutput 'vite-error.log')
  Wait-QuizEndpoint 'http://127.0.0.1:5174/'
  Push-Location $quizFrontend
  try {
    $quizOutputSuffix=if($TestFilter){'-focused'}else{'-live'}
    $quizBrowserOutput='test-results/'+($Spec -replace '\.spec\.ts$','')+$quizOutputSuffix
    $quizBrowserArgs=@('node_modules/@playwright/test/cli.js','test',("e2e/"+$Spec),'--reporter=line','--output',$quizBrowserOutput)
    if($TestFilter){$quizBrowserArgs+=@('--grep',$TestFilter)}
    & $quizNode @quizBrowserArgs
    $quizTestExit=$LASTEXITCODE
  }finally{Pop-Location}
  if($quizTestExit -ne 0){throw "Browser checks failed ($quizTestExit). See test-results and isolated service logs."}
} finally {
  foreach($process in @($quizViteProcess,$quizBackendProcess,$quizCaptureProcess)){if($null -ne $process -and -not $process.HasExited){Stop-Process -Id $process.Id -ErrorAction SilentlyContinue}}
  if($quizContainerStarted){& docker stop $quizContainer | Out-Null}
  foreach($key in $quizEnvKeys){[Environment]::SetEnvironmentVariable($key,$quizOldEnv[$key],'Process')}
}
