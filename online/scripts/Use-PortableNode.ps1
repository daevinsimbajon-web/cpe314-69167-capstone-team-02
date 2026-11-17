# Optional: use the ignored portable Node installation provisioned during development.
if (Get-Command node -ErrorAction SilentlyContinue) { node --version; return }
$quizTools = Join-Path $PSScriptRoot '../.tools'
$quizNode = Get-ChildItem -LiteralPath $quizTools -Directory -Filter 'node-*-win-x64' | Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $quizNode) { throw 'Install Node.js 22 LTS and use node/npm on PATH, or provision a portable Node under online/.tools.' }
$env:PATH = $quizNode.FullName + ';' + $env:PATH
node --version
