[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [string] $CliPath,
    [string] $DesktopPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($CliPath)) {
    $CliPath = Join-Path $repoRoot "windows\src\OjNexus.Windows.Cli\bin\$Configuration\net8.0\ojnexus.exe"
}
if ([string]::IsNullOrWhiteSpace($DesktopPath)) {
    $DesktopPath = Join-Path $repoRoot "windows\src\OjNexus.Windows.Desktop\bin\$Configuration\net8.0-windows\OjNexus.Windows.Desktop.exe"
}

if (-not (Test-Path -LiteralPath $CliPath -PathType Leaf)) {
    throw "CLI binary is missing: $CliPath"
}
if (-not (Test-Path -LiteralPath $DesktopPath -PathType Leaf)) {
    throw "Desktop binary is missing: $DesktopPath"
}
$CliPath = (Resolve-Path -LiteralPath $CliPath).Path
$DesktopPath = (Resolve-Path -LiteralPath $DesktopPath).Path
Write-Host "CLI PATH: $CliPath"
Write-Host "DESKTOP PATH: $DesktopPath"

$smokeDataDirectory = Join-Path ([IO.Path]::GetTempPath()) "ojnexus-smoke-$([Guid]::NewGuid().ToString('N'))"
$previousDataDirectory = [Environment]::GetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', 'Process')
$cliProcess = $null
$desktopProcess = $null

try {
    New-Item -ItemType Directory -Path $smokeDataDirectory -Force | Out-Null
    [Environment]::SetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', $smokeDataDirectory, 'Process')
    Write-Host "SMOKE DATA DIRECTORY: $smokeDataDirectory"

    $cliStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $cliStartInfo.FileName = $CliPath
    $cliStartInfo.Arguments = 'status --json'
    $cliStartInfo.WorkingDirectory = Split-Path -Parent $CliPath
    $cliStartInfo.UseShellExecute = $false
    $cliStartInfo.CreateNoWindow = $true
    $cliStartInfo.RedirectStandardOutput = $true
    $cliStartInfo.RedirectStandardError = $true
    $cliProcess = [Diagnostics.Process]::new()
    $cliProcess.StartInfo = $cliStartInfo
    if (-not $cliProcess.Start()) {
        throw "CLI process failed to start: $CliPath"
    }
    $cliStdout = $cliProcess.StandardOutput.ReadToEnd()
    $cliStderr = $cliProcess.StandardError.ReadToEnd()
    $cliProcess.WaitForExit()
    Write-Host "CLI EXIT CODE: $($cliProcess.ExitCode)"
    Write-Host "CLI STDOUT: $($cliStdout.Trim())"
    if (-not [string]::IsNullOrWhiteSpace($cliStderr)) {
        Write-Host "CLI STDERR: $($cliStderr.Trim())"
    }
    if ($cliProcess.ExitCode -ne 0) {
        throw "CLI status returned exit code $($cliProcess.ExitCode)."
    }
    try {
        $status = $cliStdout | ConvertFrom-Json
    }
    catch {
        throw "CLI status output is not valid JSON: $($_.Exception.Message)"
    }
    if ($null -eq $status -or $status.status -ne 'ready') {
        throw 'CLI status JSON did not contain status=ready.'
    }
    $databasePath = Join-Path $smokeDataDirectory 'ojnexus.db'
    if (-not (Test-Path -LiteralPath $databasePath -PathType Leaf)) {
        throw "CLI did not create the expected smoke database: $databasePath"
    }

    $desktopStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $desktopStartInfo.FileName = $DesktopPath
    $desktopStartInfo.WorkingDirectory = Split-Path -Parent $DesktopPath
    $desktopStartInfo.UseShellExecute = $false
    $desktopStartInfo.CreateNoWindow = $true
    $desktopProcess = [Diagnostics.Process]::new()
    $desktopProcess.StartInfo = $desktopStartInfo
    if (-not $desktopProcess.Start()) {
        throw "Desktop process failed to start: $DesktopPath"
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while (-not $desktopProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 250
    }
    if ($desktopProcess.HasExited) {
        throw "Desktop process exited before the 15-second deadline with code $($desktopProcess.ExitCode)."
    }
    Write-Host 'DESKTOP START: ALIVE WITHIN 15 SECONDS'
}
finally {
    if ($null -ne $desktopProcess) {
        if (-not $desktopProcess.HasExited) {
            $desktopProcess.Kill()
            $desktopProcess.WaitForExit(5000) | Out-Null
        }
        $desktopProcess.Dispose()
    }
    if ($null -ne $cliProcess) {
        $cliProcess.Dispose()
    }
    [Environment]::SetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', $previousDataDirectory, 'Process')
    if (Test-Path -LiteralPath $smokeDataDirectory -PathType Container) {
        Remove-Item -LiteralPath $smokeDataDirectory -Recurse -Force
    }
}
