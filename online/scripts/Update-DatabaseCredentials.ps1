# Align the existing database role with online/.env without replacing the data volume.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Use-Env.ps1')
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$databaseContainer = 'online-postgres-1'
$previousUser = (& docker exec $databaseContainer printenv POSTGRES_USER).Trim()
$databaseName = (& docker exec $databaseContainer printenv POSTGRES_DB).Trim()
if ($LASTEXITCODE -ne 0) { throw 'The existing PostgreSQL container is unavailable.' }
if ($env:POSTGRES_DB -cne $databaseName) { throw 'Database name changed. This helper only rotates credentials.' }
if ([string]::IsNullOrWhiteSpace($env:POSTGRES_USER) -or [Text.Encoding]::UTF8.GetByteCount($env:POSTGRES_USER) -gt 63) {
    throw 'Choose a database username containing 1 to 63 UTF-8 bytes.'
}
if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) { throw 'Set a nonempty database password first.' }
function Quote-DatabaseIdentifier([string]$Value) { return '"' + $Value.Replace('"','""') + '"' }
function Quote-DatabaseLiteral([string]$Value) { return "'" + $Value.Replace("'","''") + "'" }
function Invoke-PrivateDatabaseSql([string]$User, [string]$Sql) {
    $privateResult = $Sql | & docker exec -i $databaseContainer psql -X -v ON_ERROR_STOP=1 -U $User -d $databaseName -At 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database credential update failed. Private SQL output was withheld.' }
    return $privateResult
}
$newIdentifier = Quote-DatabaseIdentifier $env:POSTGRES_USER
$oldIdentifier = Quote-DatabaseIdentifier $previousUser
$passwordLiteral = Quote-DatabaseLiteral $env:POSTGRES_PASSWORD
$maintenanceUser = 'hootly_rotation_' + [Guid]::NewGuid().ToString('N').Substring(0,16)
$maintenanceIdentifier = Quote-DatabaseIdentifier $maintenanceUser
$maintenanceCreated = $false
$roleRenamed = $false
try {
    if ($previousUser -cne $env:POSTGRES_USER) {
        $exists = Invoke-PrivateDatabaseSql $previousUser ('SELECT count(*) FROM pg_roles WHERE rolname=' + (Quote-DatabaseLiteral $env:POSTGRES_USER))
        if (($exists -join '').Trim() -ne '0') { throw 'The chosen username already belongs to another role; no roles were changed.' }
        # PostgreSQL cannot rename the currently authenticated role. A temporary,
        # passwordless local maintenance role performs the atomic rename instead.
        Invoke-PrivateDatabaseSql $previousUser "CREATE ROLE $maintenanceIdentifier LOGIN SUPERUSER" | Out-Null
        $maintenanceCreated = $true
        Invoke-PrivateDatabaseSql $maintenanceUser "BEGIN; SET LOCAL log_statement='none'; ALTER ROLE $oldIdentifier RENAME TO $newIdentifier; ALTER ROLE $newIdentifier PASSWORD $passwordLiteral; COMMIT;" | Out-Null
        $roleRenamed = $true
    } else {
        Invoke-PrivateDatabaseSql $previousUser "BEGIN; SET LOCAL log_statement='none'; ALTER ROLE $newIdentifier PASSWORD $passwordLiteral; COMMIT;" | Out-Null
    }
    $credentialInput = (@($env:POSTGRES_USER,$env:POSTGRES_PASSWORD,$env:POSTGRES_DB,'SELECT 1;') -join "`n") + "`n"
    $credentialResult = $credentialInput | & docker exec -i $databaseContainer sh -c 'IFS= read -r PGUSER; IFS= read -r PGPASSWORD; IFS= read -r PGDATABASE; export PGUSER PGPASSWORD PGDATABASE; psql -h 127.0.0.1 -At' 2>&1
    if ($LASTEXITCODE -ne 0 -or ($credentialResult -join '').Trim() -ne '1') {
        throw 'The role update completed, but TCP credential verification failed. Do not recreate the database.'
    }
    Write-Output 'Database credentials updated and verified. Existing role ownership and game data were preserved.'
} finally {
    if ($maintenanceCreated) {
        $cleanupUser = if ($roleRenamed) { $env:POSTGRES_USER } else { $previousUser }
        Invoke-PrivateDatabaseSql $cleanupUser "DROP ROLE $maintenanceIdentifier" | Out-Null
    }
}
