# Dot-source after a new Docker Desktop install when this terminal still has an old PATH.
# Changes only this PowerShell process; never edits registry, Git config, or credentials.
$quizDockerCommand = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue
$quizDockerExecutable = $null
if ($null -ne $quizDockerCommand) {
    $quizDockerExecutable = $quizDockerCommand.Source
} else {
    $quizDockerCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'),
        (Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe')
    )
    foreach ($quizDockerCandidate in $quizDockerCandidates) {
        if (Test-Path -LiteralPath $quizDockerCandidate -PathType Leaf) {
            $quizDockerExecutable = $quizDockerCandidate
            break
        }
    }
}
if ($null -eq $quizDockerExecutable) {
    throw 'Docker Desktop is not installed or its CLI could not be found. Install and start Docker Desktop first.'
}
$quizDockerBin = [System.IO.Path]::GetDirectoryName($quizDockerExecutable)
if (($env:PATH -split ';') -notcontains $quizDockerBin) {
    $env:PATH = $quizDockerBin + ';' + $env:PATH
}
& $quizDockerExecutable --version
if ($LASTEXITCODE -ne 0) { throw 'Docker CLI could not run. Check the Docker Desktop installation.' }
