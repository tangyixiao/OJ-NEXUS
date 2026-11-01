$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '..\scripts\storage-acceptance-readiness.ps1')

$state = [pscustomobject]@{ Reads = 0 }
$transitioned = Wait-StorageState {
    $state.Reads++
    if ($state.Reads -lt 3) { 'CHECKING' } else { 'INTEGRITY_FAILURE' }
} 'INTEGRITY_FAILURE' 2
if ($transitioned -ne 'INTEGRITY_FAILURE' -or $state.Reads -lt 3) {
    throw 'Readiness barrier did not wait through the initially present CHECKING state.'
}

$rejected = $false
try {
    Wait-StorageState { 'STORAGE_BUSY' } 'INTEGRITY_FAILURE' 1 | Out-Null
}
catch {
    $rejected = $_.Exception.Message.Contains("expected value 'INTEGRITY_FAILURE'") -and
        $_.Exception.Message.Contains("actual: 'STORAGE_BUSY'")
}
if (-not $rejected) { throw 'Readiness barrier did not reject and diagnose a wrong final code.' }

Write-Output "Storage acceptance readiness regression: PASS (CHECKING -> INTEGRITY_FAILURE after $($state.Reads) reads; wrong code rejected with actual value)"
