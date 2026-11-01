function Wait-StorageState($readState, [string] $expected, [int] $seconds = 15) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    $actual = $null
    do {
        $actual = & $readState
        if ($actual -eq $expected) { return $actual }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "UI state did not reach expected value '$expected' within ${seconds}s; actual: '$actual'"
}
