$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '..\storage-owned-process.ps1')

$outputDirectory = Join-Path ([IO.Path]::GetTempPath()) ('ojnexus-owned-process-test-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($outputDirectory)
$stdoutPath = Join-Path $outputDirectory 'stdout.txt'
$stderrPath = Join-Path $outputDirectory 'stderr.txt'
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = Join-Path $PSHOME 'pwsh.exe'
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
[void]$startInfo.ArgumentList.Add('-NoProfile')
[void]$startInfo.ArgumentList.Add('-Command')
[void]$startInfo.ArgumentList.Add("[Console]::Out.WriteLine('owned-stdout-marker'); [Console]::Out.Flush(); [Console]::Error.WriteLine('owned-stderr-marker'); [Console]::Error.Flush(); Start-Sleep -Seconds 30")
$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
if (-not $process.Start()) { throw 'Could not start owned timeout-test process.' }
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$result = Wait-OwnedProcessWithCapture -Process $process -OwnedProcesses $ownedProcesses -TimeoutSeconds 1 -KillWaitSeconds 3 -StdoutPath $stdoutPath -StderrPath $stderrPath

if (-not $result.TimedOut) { throw 'Parent timeout was not reported.' }
if (-not $result.Exited -or -not $result.ExitedAfterKill) { throw 'Owned child did not exit after parent termination.' }
if ($ownedProcesses.Count -ne 1 -or $ownedProcesses[0].Id -ne $result.ProcessId) { throw 'Timed child was not registered as owned.' }
if (-not (Get-Content -Raw $stdoutPath).Contains('owned-stdout-marker')) { throw 'Partial stdout was not retained.' }
if (-not (Get-Content -Raw $stderrPath).Contains('owned-stderr-marker')) { throw 'Partial stderr was not retained.' }
$process.Dispose()
Write-Output "OWNED PROCESS TIMEOUT: PASS / PID=$($result.ProcessId) / TIMED_OUT=$($result.TimedOut) / EXITED_AFTER_KILL=$($result.ExitedAfterKill) / OUTPUTS=RETAINED"
