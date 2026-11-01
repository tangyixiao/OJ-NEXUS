function Wait-OwnedProcessWithCapture {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][Diagnostics.Process] $Process,
        [Parameter(Mandatory)][AllowEmptyCollection()][Collections.Generic.List[Diagnostics.Process]] $OwnedProcesses,
        [Parameter(Mandatory)][ValidateRange(1, 120)][int] $TimeoutSeconds,
        [Parameter(Mandatory)][string] $StdoutPath,
        [Parameter(Mandatory)][string] $StderrPath,
        [ValidateRange(1, 30)][int] $KillWaitSeconds = 3
    )

    $OwnedProcesses.Add($Process)
    $stdoutTask = $Process.StandardOutput.ReadToEndAsync()
    $stderrTask = $Process.StandardError.ReadToEndAsync()
    $timedOut = -not $Process.WaitForExit($TimeoutSeconds * 1000)
    $exitedAfterKill = $true
    if ($timedOut) {
        try { $Process.Kill($true) }
        catch [InvalidOperationException] { }
        $exitedAfterKill = $Process.WaitForExit($KillWaitSeconds * 1000)
    }

    $stdout = try { $stdoutTask.WaitAsync([TimeSpan]::FromSeconds($KillWaitSeconds)).GetAwaiter().GetResult() }
    catch { '[stdout drain incomplete]' }
    $stderr = try { $stderrTask.WaitAsync([TimeSpan]::FromSeconds($KillWaitSeconds)).GetAwaiter().GetResult() }
    catch { '[stderr drain incomplete]' }
    [IO.File]::WriteAllText($StdoutPath, $stdout)
    [IO.File]::WriteAllText($StderrPath, $stderr)

    $Process.Refresh()
    return [pscustomobject]@{
        TimedOut = $timedOut
        Exited = $Process.HasExited
        ExitedAfterKill = $exitedAfterKill
        ExitCode = if ($Process.HasExited) { $Process.ExitCode } else { $null }
        ProcessId = $Process.Id
        Stdout = $stdout
        Stderr = $stderr
        StdoutPath = $StdoutPath
        StderrPath = $StderrPath
    }
}
