# Dot-source from the repository root: . ./online/scripts/Use-Env.ps1
$quizEnvFile = Join-Path $PSScriptRoot '../.env'
if (-not (Test-Path -LiteralPath $quizEnvFile)) { throw 'Copy online/.env.example to online/.env and fill in local values first.' }
foreach ($quizEnvLine in [System.IO.File]::ReadAllLines($quizEnvFile)) {
    if ($quizEnvLine.Trim() -eq '' -or $quizEnvLine.TrimStart().StartsWith('#')) { continue }
    if ($quizEnvLine -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid .env entry. Use KEY=value without shell expressions.' }
    $quizEnvKey = $Matches[1]
    $quizEnvValue = $Matches[2].Trim()
    if ($quizEnvValue.Length -ge 2 -and (($quizEnvValue.StartsWith('"') -and $quizEnvValue.EndsWith('"')) -or ($quizEnvValue.StartsWith("'") -and $quizEnvValue.EndsWith("'")))) {
        $quizEnvValue = $quizEnvValue.Substring(1, $quizEnvValue.Length - 2)
    }
    Set-Item -LiteralPath ('Env:' + $quizEnvKey) -Value $quizEnvValue
}
Write-Output 'Loaded local online configuration. Values are hidden.'
