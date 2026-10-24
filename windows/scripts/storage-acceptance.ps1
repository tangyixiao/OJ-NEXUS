[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')][string] $Configuration = 'Release',
    [ValidateSet('Child', 'InProcess')][string] $HealthyUiMode = 'Child',
    [string] $EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing
. (Join-Path $PSScriptRoot 'storage-owned-process.ps1')
. (Join-Path $PSScriptRoot 'storage-acceptance-readiness.ps1')

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $tempRoot ('ojnexus-storage-acceptance-' + [Guid]::NewGuid().ToString('N'))
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
$tempPrefix = $tempRoot.TrimEnd('\') + '\'
if (-not $EvidenceDirectory.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'EvidenceDirectory must be a new child of the current Temp directory.'
}
if (Test-Path -LiteralPath $EvidenceDirectory) { throw "EvidenceDirectory already exists: $EvidenceDirectory" }
[void][IO.Directory]::CreateDirectory($EvidenceDirectory)

$cli = Join-Path $repoRoot "windows\src\OjNexus.Windows.Cli\bin\$Configuration\net8.0\ojnexus.exe"
$cliAssembly = Join-Path $repoRoot "windows\src\OjNexus.Windows.Cli\bin\$Configuration\net8.0\ojnexus.dll"
$coreAssembly = Join-Path $repoRoot "windows\src\OjNexus.Windows.Core\bin\$Configuration\net8.0\OjNexus.Windows.Core.dll"
$desktop = Join-Path $repoRoot "windows\src\OjNexus.Windows.Desktop\bin\$Configuration\net8.0-windows\OjNexus.Windows.Desktop.exe"
$desktopAssembly = Join-Path $repoRoot "windows\src\OjNexus.Windows.Desktop\bin\$Configuration\net8.0-windows\OjNexus.Windows.Desktop.dll"
$hostDll = Join-Path $repoRoot "windows\tests\OjNexus.Windows.StorageTestHost\bin\$Configuration\net8.0\OjNexus.Windows.StorageTestHost.dll"
foreach ($path in @($cli, $cliAssembly, $coreAssembly, $desktop, $desktopAssembly, $hostDll)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required built binary is missing: $path" } }

function New-OwnedRoot([string] $name) {
    $root = Join-Path $EvidenceDirectory ($name + '-' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($root)
    if ((Get-Item -LiteralPath $root -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Generated data root is a reparse point.' }
    return $root
}
function Get-FileDigest([string] $path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return @{ exists = $false; length = 0; sha256 = $null } }
    $item = Get-Item -LiteralPath $path
    return @{ exists = $true; length = $item.Length; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
}
function Start-OwnedProcess([string] $file, [string[]] $arguments, [string] $root, [bool] $redirect = $false) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $file
    $info.WorkingDirectory = $repoRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $redirect
    $info.RedirectStandardInput = $redirect
    $info.RedirectStandardOutput = $redirect
    $info.RedirectStandardError = $redirect
    $info.Environment['OJ_NEXUS_DATA_DIRECTORY'] = $root
    foreach ($argument in $arguments) { [void]$info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "Failed to start owned process: $file" }
    return $process
}
function Wait-ProcessBounded([Diagnostics.Process] $process, [int] $seconds = 20) {
    if (-not $process.WaitForExit($seconds * 1000)) { throw "Owned process $($process.Id) did not exit within $seconds seconds." }
    $process.Refresh()
    return $process.ExitCode
}
function Get-Window([Diagnostics.Process] $process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $process.Refresh()
        if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
            return [Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "No top-level WPF window appeared for process $($process.Id)."
}
function Find-AutomationId($root, [string] $id) {
    $condition = [Windows.Automation.PropertyCondition]::new([Windows.Automation.AutomationElement]::AutomationIdProperty, $id)
    return $root.FindFirst([Windows.Automation.TreeScope]::Descendants, $condition)
}
function Wait-AutomationId($root, [string] $id, [int] $seconds = 15, [Diagnostics.Process] $process) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    do {
        if ($null -ne $process) {
            $process.Refresh()
            if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
                $freshRoot = [Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
                if ($null -ne $freshRoot) { $root = $freshRoot }
            }
        }
        $element = Find-AutomationId $root $id
        if ($null -ne $element) { return $element }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    $seen = [Collections.Generic.List[string]]::new()
    $walker = [Windows.Automation.TreeWalker]::ControlViewWalker
    $pending = [Collections.Generic.Stack[Windows.Automation.AutomationElement]]::new()
    $first = $walker.GetFirstChild($root)
    if ($null -ne $first) { $pending.Push($first) }
    while ($pending.Count -gt 0 -and $seen.Count -lt 100) {
        $current = $pending.Pop()
        $seen.Add("$($current.Current.ControlType.ProgrammaticName):$($current.Current.Name):$($current.Current.AutomationId)")
        $child = $walker.GetFirstChild($current)
        if ($null -ne $child) { $pending.Push($child) }
        $sibling = $walker.GetNextSibling($current)
        if ($null -ne $sibling) { $pending.Push($sibling) }
    }
    $details = [Collections.Generic.List[string]]::new()
    $details.Add("utc=$([DateTime]::UtcNow.ToString('O'))")
    $details.Add("powershellPid=$PID")
    try {
        $info = $root.Current
        $details.Add("rootName=$($info.Name)")
        $details.Add("rootAutomationId=$($info.AutomationId)")
        $details.Add("rootClassName=$($info.ClassName)")
        $details.Add("rootControlType=$($info.ControlType.ProgrammaticName)")
        $details.Add("rootFrameworkId=$($info.FrameworkId)")
        $details.Add("rootRuntimeId=$($root.GetRuntimeId() -join '.')")
        $details.Add("rootNativeWindowHandle=0x$($info.NativeWindowHandle.ToString('x'))")
        $details.Add("rootProviderProcessId=$($info.ProcessId)")
        $targetProcess = Get-Process -Id $info.ProcessId -ErrorAction Stop
        $details.Add("targetProcessName=$($targetProcess.ProcessName)")
        $details.Add("targetMainWindowHandle=0x$($targetProcess.MainWindowHandle.ToInt64().ToString('x'))")
    }
    catch { $details.Add("rootReadFailure=$($_.Exception.GetType().FullName):$($_.Exception.Message)") }
    $details.Add("controlTree=$($seen -join ' | ')")
    $details.Add("requestedAutomationId=$id")
    $diagnosticPath = Join-Path $EvidenceDirectory 'ui-automation-diagnostics.txt'
    [IO.File]::WriteAllLines($diagnosticPath, $details)
    $manifest.uiAutomationDiagnostic = $diagnosticPath
    if ($null -ne $process) {
        try {
            $probeScript = Join-Path $PSScriptRoot 'storage-uia-probe.ps1'
            $probeProcess = Start-OwnedProcess (Join-Path $PSHOME 'pwsh.exe') @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $probeScript, '-ProcessId', "$($process.Id)", '-AutomationId', $id, '-TimeoutSeconds', '3') $EvidenceDirectory $true
            $probeCapture = Wait-OwnedProcessWithCapture -Process $probeProcess -OwnedProcesses $ownedProcesses -TimeoutSeconds 5 -StdoutPath (Join-Path $EvidenceDirectory 'ui-automation-child-probe.stdout.txt') -StderrPath (Join-Path $EvidenceDirectory 'ui-automation-child-probe.stderr.txt')
            $probePath = Join-Path $EvidenceDirectory 'ui-automation-child-probe.json'
            $probeRecord = @{
                outcome = if ($probeCapture.TimedOut) { 'timed-out' } elseif ($probeCapture.ExitCode -eq 0) { 'completed' } else { 'failed' }
                pid = $probeCapture.ProcessId
                exitCode = $probeCapture.ExitCode
                exited = $probeCapture.Exited
                exitedAfterKill = $probeCapture.ExitedAfterKill
                stdout = $probeCapture.Stdout
                stderr = $probeCapture.Stderr
                stdoutPath = $probeCapture.StdoutPath
                stderrPath = $probeCapture.StderrPath
            }
            [IO.File]::WriteAllText($probePath, ($probeRecord | ConvertTo-Json -Depth 4))
            $manifest.uiAutomationChildProbe = $probeRecord
        }
        catch { $manifest.uiAutomationChildProbeError = $_.Exception.Message }
        try {
            $screenshotPath = Join-Path $EvidenceDirectory "ui-automation-failure-$id.png"
            Save-BlockedScreenshot $process $screenshotPath
            $manifest.uiAutomationFailureScreenshot = $screenshotPath
        }
        catch { $manifest.uiAutomationFailureScreenshotError = $_.Exception.Message }
    }
    throw "AutomationId did not appear: $id; diagnostic: $diagnosticPath; UIA: $($seen -join ' | ')"
}
function Wait-ElementByName($root, [string] $name, [int] $seconds = 15) {
    $condition = [Windows.Automation.PropertyCondition]::new([Windows.Automation.AutomationElement]::NameProperty, $name)
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    do {
        $element = $root.FindFirst([Windows.Automation.TreeScope]::Descendants, $condition)
        if ($null -ne $element) { return $element }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "UI element did not appear: $name"
}
function Invoke-AutomationButton($root, [string] $id) {
    $button = Wait-AutomationId $root $id
    if (-not $button.Current.IsEnabled -or $button.Current.IsOffscreen) { throw "Button is disabled or offscreen: $id" }
    $button.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke()
}
function Save-BlockedScreenshot([Diagnostics.Process] $process, [string] $path) {
    $boundsType = @'
using System;
using System.Runtime.InteropServices;
public static class OjnexusCapture {
 [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
}
'@
    if (-not ('OjnexusCapture' -as [type])) { Add-Type -TypeDefinition $boundsType }
    $process.Refresh()
    $rect = [OjnexusCapture+RECT]::new()
    if (-not [OjnexusCapture]::GetWindowRect($process.MainWindowHandle, [ref]$rect)) { throw 'GetWindowRect failed.' }
    $width = $rect.Right - $rect.Left; $height = $rect.Bottom - $rect.Top
    if ($width -le 0 -or $height -le 0) { throw 'Window bounds are invalid.' }
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    try {
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try { $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size, [Drawing.CopyPixelOperation]::SourceCopy) }
        finally { $graphics.Dispose() }
        $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png)
    }
    finally { $bitmap.Dispose() }
}

$manifest = [ordered]@{
    startedUtc = [DateTime]::UtcNow.ToString('O'); repoRoot = $repoRoot; configuration = $Configuration
    cliPath = (Resolve-Path $cli).Path; cliSha256 = (Get-FileHash $cli -Algorithm SHA256).Hash
    cliAssemblyPath = (Resolve-Path $cliAssembly).Path; cliAssemblySha256 = (Get-FileHash $cliAssembly -Algorithm SHA256).Hash
    coreAssemblyPath = (Resolve-Path $coreAssembly).Path; coreAssemblySha256 = (Get-FileHash $coreAssembly -Algorithm SHA256).Hash
    desktopPath = (Resolve-Path $desktop).Path; desktopSha256 = (Get-FileHash $desktop -Algorithm SHA256).Hash
    desktopAssemblyPath = (Resolve-Path $desktopAssembly).Path; desktopAssemblySha256 = (Get-FileHash $desktopAssembly -Algorithm SHA256).Hash
    hostPath = (Resolve-Path $hostDll).Path; hostSha256 = (Get-FileHash $hostDll -Algorithm SHA256).Hash
    sourceHead = (& git -C $repoRoot rev-parse HEAD).Trim()
    sourceDirty = @(& git -C $repoRoot status --porcelain)
    evidenceDirectory = $EvidenceDirectory; scenarios = [Collections.Generic.List[object]]::new()
}
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
try {
    # CLI preservation and exact exit/JSON contract against an existing zero-byte database.
    $zeroRoot = New-OwnedRoot 'cli-zero'
    $zeroDb = Join-Path $zeroRoot 'ojnexus.db'
    [IO.File]::WriteAllBytes($zeroDb, [byte[]]@())
    $beforeZero = Get-FileDigest $zeroDb
    $cliRun = Start-OwnedProcess $cli @('status', '--json') $zeroRoot $true
    $ownedProcesses.Add($cliRun)
    $stdoutTask = $cliRun.StandardOutput.ReadToEndAsync(); $stderrTask = $cliRun.StandardError.ReadToEndAsync()
    $cliExit = Wait-ProcessBounded $cliRun 20
    $stdout = $stdoutTask.GetAwaiter().GetResult().Trim(); $stderr = $stderrTask.GetAwaiter().GetResult()
    $blocked = $stdout | ConvertFrom-Json
    $afterZero = Get-FileDigest $zeroDb
    if ($cliExit -ne 6 -or $blocked.status -ne 'blocked' -or $blocked.error -ne 'INTEGRITY_FAILURE' -or $beforeZero.sha256 -ne $afterZero.sha256 -or $stderr.Contains($zeroRoot)) {
        throw 'CLI zero-byte preservation scenario failed.'
    }
    $manifest.scenarios.Add(@{ name = 'cli-zero'; pid = $cliRun.Id; exitCode = $cliExit; stdout = $stdout; stderrSanitized = -not $stderr.Contains($zeroRoot); databaseBefore = $beforeZero; databaseAfter = $afterZero; root = $zeroRoot; result = 'PASS' })

    # WPF blocked state and graceful Exit against an existing damaged file.
    $exitRoot = New-OwnedRoot 'wpf-exit'
    $exitDb = Join-Path $exitRoot 'ojnexus.db'; [IO.File]::WriteAllBytes($exitDb, [byte[]]@())
    $beforeExit = Get-FileDigest $exitDb
    $exitApp = Start-OwnedProcess $desktop @() $exitRoot $false; $ownedProcesses.Add($exitApp)
    $exitWindow = Get-Window $exitApp
    $heading = Wait-AutomationId $exitWindow 'StorageStartupHeading' 15 $exitApp
    $code = Wait-AutomationId $exitWindow 'StorageFailureCode' 15 $exitApp
    $headingText = Wait-StorageState { $heading.Current.Name } 'LOCAL STORAGE UNAVAILABLE'
    $failureText = Wait-StorageState { $code.Current.Name } 'INTEGRITY_FAILURE'
    if ($null -eq (Wait-ElementByName $exitWindow 'LOCAL FILES PRESERVED')) { throw 'WPF preservation text was missing.' }
    $blockedRetry = Wait-AutomationId $exitWindow 'RetryStorageOpen' 15 $exitApp
    if ($blockedRetry.Current.IsOffscreen -or -not $blockedRetry.Current.IsEnabled) { throw 'Blocked Retry Open control was not visible and enabled.' }
    $failureCode = $failureText
    $navCondition = [Windows.Automation.PropertyCondition]::new([Windows.Automation.AutomationElement]::NameProperty, 'DASHBOARD')
    if ($null -ne $exitWindow.FindFirst([Windows.Automation.TreeScope]::Descendants, $navCondition)) { throw 'Normal page navigation was exposed before Ready.' }
    $exitShot = Join-Path $EvidenceDirectory 'wpf-integrity-blocked.png'; Save-BlockedScreenshot $exitApp $exitShot
    Invoke-AutomationButton $exitWindow 'ExitStorage'
    $exitCode = Wait-ProcessBounded $exitApp 20
    $afterExit = Get-FileDigest $exitDb
    if ($exitCode -ne 0 -or $beforeExit.sha256 -ne $afterExit.sha256) { throw 'WPF Exit did not preserve the original database.' }
    $manifest.scenarios.Add(@{ name = 'wpf-blocked-exit'; pid = $exitApp.Id; exitCode = $exitCode; blockedHeading = $headingText; failureCode = $failureCode; screenshot = $exitShot; screenshotSha256 = (Get-FileHash $exitShot -Algorithm SHA256).Hash; databaseBefore = $beforeExit; databaseAfter = $afterExit; root = $exitRoot; result = 'PASS' })

    # A real second-process exclusive lease must produce STORAGE_BUSY; Retry succeeds after release.
    $busyRoot = New-OwnedRoot 'wpf-retry'
    $seed = Start-OwnedProcess $cli @('status', '--json') $busyRoot $true; $ownedProcesses.Add($seed)
    $seedOut = $seed.StandardOutput.ReadToEndAsync(); $seedErr = $seed.StandardError.ReadToEndAsync()
    if ((Wait-ProcessBounded $seed 20) -ne 0 -or ($seedOut.GetAwaiter().GetResult() | ConvertFrom-Json).status -ne 'ready') { throw 'Could not seed healthy shared-storage fixture.' }
    $hostProcess = Start-OwnedProcess 'dotnet' @($hostDll, $busyRoot, 'exclusive') $busyRoot $true; $ownedProcesses.Add($hostProcess)
    $hostReady = $hostProcess.StandardOutput.ReadLineAsync().WaitAsync([TimeSpan]::FromSeconds(10)).GetAwaiter().GetResult()
    if ($hostReady -ne 'READY') { throw "Exclusive host failed its acquisition barrier: $hostReady" }
    $retryApp = Start-OwnedProcess $desktop @() $busyRoot $false; $ownedProcesses.Add($retryApp)
    $retryWindow = Get-Window $retryApp
    $busyCode = Wait-AutomationId $retryWindow 'StorageFailureCode' 15 $retryApp
    $busyHeading = Wait-AutomationId $retryWindow 'StorageStartupHeading' 15 $retryApp
    $busyHeadingText = Wait-StorageState { $busyHeading.Current.Name } 'LOCAL STORAGE UNAVAILABLE'
    $busyCodeText = Wait-StorageState { $busyCode.Current.Name } 'STORAGE_BUSY'
    $busyShot = Join-Path $EvidenceDirectory 'wpf-storage-busy.png'; Save-BlockedScreenshot $retryApp $busyShot
    $hostProcess.StandardInput.WriteLine('release'); $hostProcess.StandardInput.Flush()
    if ((Wait-ProcessBounded $hostProcess 10) -ne 0) { throw 'Exclusive lease host did not exit cleanly.' }
    Invoke-AutomationButton $retryWindow 'RetryStorageOpen'
    $null = Wait-ElementByName $retryWindow 'LOCAL STATE' 15
    $manifest.scenarios.Add(@{ name = 'wpf-busy-retry'; desktopPid = $retryApp.Id; hostPid = $hostProcess.Id; hostBarrier = $hostReady; blockedHeading = $busyHeadingText; blockedCode = $busyCodeText; screenshot = $busyShot; screenshotSha256 = (Get-FileHash $busyShot -Algorithm SHA256).Hash; retryVisible = $true; readyHeading = 'LOCAL STATE'; root = $busyRoot; result = 'PASS' })

    # The CLI remains a valid shared reader while WPF owns its ready session.
    $coexist = Start-OwnedProcess $cli @('status', '--json') $busyRoot $true; $ownedProcesses.Add($coexist)
    $coexistOut = $coexist.StandardOutput.ReadToEndAsync(); $coexistErr = $coexist.StandardError.ReadToEndAsync()
    $coexistExit = Wait-ProcessBounded $coexist 20
    $coexistJson = $coexistOut.GetAwaiter().GetResult() | ConvertFrom-Json
    if ($coexistExit -ne 0 -or $coexistJson.status -ne 'ready') { throw 'CLI did not coexist with the WPF shared session.' }
    if (-not $retryApp.CloseMainWindow()) { throw 'Could not send a graceful close request to the ready WPF shell.' }
    $readyCloseExit = Wait-ProcessBounded $retryApp 20
    if ($readyCloseExit -ne 0) { throw "WPF ready-shell graceful close exited with code $readyCloseExit." }
    $manifest.scenarios.Add(@{ name = 'shared-coexistence'; cliExitCode = $coexistExit; cliStatus = $coexistJson.status; wpfPid = $retryApp.Id; wpfCloseRequest = 'CloseMainWindow'; wpfExitCode = $readyCloseExit; result = 'PASS' })

    # Existing four-page smoke writes only to a fresh evidence subdirectory.
    $uiEvidence = Join-Path $EvidenceDirectory 'healthy-ui'
    $uiScript = Join-Path $PSScriptRoot 'ui-smoke.ps1'
    $smokeLog = Join-Path $EvidenceDirectory 'healthy-ui-smoke.log'
    $smokeProcess = $null
    if ($HealthyUiMode -eq 'InProcess') {
        try { & $uiScript -Configuration $Configuration -OutputDirectory $uiEvidence *>&1 | Tee-Object -FilePath $smokeLog | Out-Null }
        catch { $manifest.healthyUiFailure = $_.Exception.Message; throw }
        $smokeExit = 0
        $smokeOutput = Get-Content -LiteralPath $smokeLog -Raw
    }
    else {
        $smokeArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $uiScript, '-Configuration', $Configuration, '-OutputDirectory', $uiEvidence)
        $smokeProcess = Start-OwnedProcess (Join-Path $PSHOME 'pwsh.exe') $smokeArgs $uiEvidence $true
        $ownedProcesses.Add($smokeProcess)
        $smokeStdout = $smokeProcess.StandardOutput.ReadToEndAsync()
        $smokeStderr = $smokeProcess.StandardError.ReadToEndAsync()
        $smokeExit = Wait-ProcessBounded $smokeProcess 90
        $smokeOutput = $smokeStdout.GetAwaiter().GetResult()
        $smokeErrors = $smokeStderr.GetAwaiter().GetResult()
        @("exitCode=$smokeExit", '--- stdout ---', $smokeOutput, '--- stderr ---', $smokeErrors) | Set-Content -LiteralPath $smokeLog -Encoding utf8
        if ($smokeExit -ne 0 -or -not $smokeOutput.Contains('UI SMOKE: PASS')) { throw "Healthy four-page UI smoke failed with child pwsh exit $smokeExit; see $smokeLog" }
    }
    $screenshots = @('dashboard.png', 'connectors.png', 'history.png', 'problems.png') | ForEach-Object {
        $path = Join-Path $uiEvidence $_
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing healthy UI screenshot: $_" }
        @{ name = $_; sha256 = (Get-FileHash $path -Algorithm SHA256).Hash; length = (Get-Item -LiteralPath $path).Length }
    }
    $manifest.scenarios.Add(@{ name = 'healthy-four-pages'; mode = $HealthyUiMode; uiPowerShellPid = if ($null -ne $smokeProcess) { $smokeProcess.Id } else { $PID }; exitCode = $smokeExit; log = $smokeLog; screenshotCount = $screenshots.Count; screenshots = $screenshots; evidence = $uiEvidence; result = 'PASS' })
    $manifest.result = 'PASS'
}
catch {
    $manifest.result = 'FAIL'
    $manifest.failure = $_.Exception.Message
    throw
}
finally {
    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) { $process.Kill($true); $null = $process.WaitForExit(5000) }
            $process.Dispose()
        }
        catch { }
    }
    $manifest.finishedUtc = [DateTime]::UtcNow.ToString('O')
    $manifestPath = Join-Path $EvidenceDirectory 'manifest.json'
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
}

Write-Output "STORAGE ACCEPTANCE: $($manifest.result)"
Write-Output "MANIFEST: $manifestPath"
