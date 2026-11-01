[CmdletBinding()]
param(
    [Parameter(Mandatory)][int] $ProcessId,
    [Parameter(Mandatory)][string] $AutomationId,
    [ValidateRange(1, 10)][int] $TimeoutSeconds = 3
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
$target = Get-Process -Id $ProcessId -ErrorAction Stop
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$found = $null
$rootInfo = $null
do {
    $target.Refresh()
    $handle = $target.MainWindowHandle
    if ($handle -ne [IntPtr]::Zero) {
        $root = [Windows.Automation.AutomationElement]::FromHandle($handle)
        if ($null -ne $root) {
            $rootInfo = $root.Current
            $condition = [Windows.Automation.PropertyCondition]::new(
                [Windows.Automation.AutomationElement]::AutomationIdProperty,
                $AutomationId)
            $found = $root.FindFirst([Windows.Automation.TreeScope]::Descendants, $condition)
            if ($null -ne $found) { break }
        }
    }
    Start-Sleep -Milliseconds 100
} while ([DateTime]::UtcNow -lt $deadline)

$result = [ordered]@{
    powershellPid = $PID
    targetProcessId = $ProcessId
    targetMainWindowHandle = if ($target.MainWindowHandle -eq [IntPtr]::Zero) { $null } else { '0x' + $target.MainWindowHandle.ToInt64().ToString('x') }
    found = $null -ne $found
    frameworkId = if ($null -ne $rootInfo) { $rootInfo.FrameworkId } else { $null }
    className = if ($null -ne $rootInfo) { $rootInfo.ClassName } else { $null }
    rootName = if ($null -ne $rootInfo) { $rootInfo.Name } else { $null }
    runtimeId = if ($null -ne $root) { $root.GetRuntimeId() -join '.' } else { $null }
    automationId = if ($null -ne $found) { $found.Current.AutomationId } else { $null }
    name = if ($null -ne $found) { $found.Current.Name } else { $null }
    controlType = if ($null -ne $found) { $found.Current.ControlType.ProgrammaticName } else { $null }
}
$result | ConvertTo-Json -Compress
