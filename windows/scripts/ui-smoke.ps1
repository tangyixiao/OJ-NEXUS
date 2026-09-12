[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [string] $DesktopPath,
    [string] $OutputDirectory,
    [ValidateRange(900, 4000)]
    [int] $Width = 900,
    [ValidateRange(560, 3000)]
    [int] $Height = 560
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($DesktopPath)) {
    $DesktopPath = Join-Path $repoRoot "windows\src\OjNexus.Windows.Desktop\bin\$Configuration\net8.0-windows\OjNexus.Windows.Desktop.exe"
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot 'windows\artifacts\ui-smoke'
}

if (-not (Test-Path -LiteralPath $DesktopPath -PathType Leaf)) {
    throw "Desktop binary is missing: $DesktopPath"
}
$DesktopPath = (Resolve-Path -LiteralPath $DesktopPath).Path
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
foreach ($screenshotName in @('dashboard.png', 'connectors.png', 'history.png')) {
    $staleScreenshot = Join-Path $OutputDirectory $screenshotName
    if (Test-Path -LiteralPath $staleScreenshot -PathType Leaf) {
        Remove-Item -LiteralPath $staleScreenshot -Force
    }
}

Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class OjNexusUiSmokeNative
{
    [DllImport("user32.dll")]
    private static extern bool SetWindowPos(
        IntPtr hWnd,
        IntPtr hWndInsertAfter,
        int x,
        int y,
        int cx,
        int cy,
        uint flags);

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    private static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool UpdateWindow(IntPtr hWnd);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    public static void ResizeAndShow(IntPtr hWnd, int width, int height)
    {
        const uint SWP_NOZORDER = 0x0004;
        const uint SWP_SHOWWINDOW = 0x0040;
        if (!SetWindowPos(hWnd, IntPtr.Zero, 10, 10, width, height, SWP_NOZORDER | SWP_SHOWWINDOW))
        {
            throw new InvalidOperationException("SetWindowPos failed.");
        }
    }

    public static int[] GetWindowBounds(IntPtr hWnd)
    {
        if (!GetWindowRect(hWnd, out var rect))
        {
            throw new InvalidOperationException("GetWindowRect failed.");
        }

        return new[] { rect.Left, rect.Top, rect.Right, rect.Bottom };
    }

    public static bool CaptureWindow(IntPtr hWnd, IntPtr hdc)
    {
        const uint PW_RENDERFULLCONTENT = 0x00000002;
        return PrintWindow(hWnd, hdc, PW_RENDERFULLCONTENT);
    }

    public static void ActivateWindow(IntPtr hWnd)
    {
        const int SW_RESTORE = 9;
        ShowWindow(hWnd, SW_RESTORE);
        SetForegroundWindow(hWnd);
        UpdateWindow(hWnd);
    }
}
'@
Add-Type -AssemblyName System.Drawing

function Find-DescendantByName {
    param(
        [System.Windows.Automation.AutomationElement] $Root,
        [string] $Name
    )

    $condition = [System.Windows.Automation.PropertyCondition]::new(
        [System.Windows.Automation.AutomationElement]::NameProperty,
        $Name)
    return $Root.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
}

function Require-DescendantByName {
    param(
        [System.Windows.Automation.AutomationElement] $Root,
        [string] $Name
    )

    $element = Find-DescendantByName -Root $Root -Name $Name
    if ($null -eq $element) {
        throw "UI element was not found: $Name"
    }

    return $element
}

function Invoke-ButtonByName {
    param(
        [System.Windows.Automation.AutomationElement] $Root,
        [string] $Name
    )

    $button = Require-DescendantByName -Root $Root -Name $Name
    $pattern = $button.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
    $pattern.Invoke()
}

function Wait-ForElement {
    param(
        [System.Windows.Automation.AutomationElement] $Root,
        [string] $Name,
        [int] $TimeoutMilliseconds = 5000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
    do {
        $element = Find-DescendantByName -Root $Root -Name $Name
        if ($null -ne $element) {
            return $element
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "UI element did not appear within ${TimeoutMilliseconds}ms: $Name"
}

function Save-WindowScreenshot {
    param(
        [IntPtr] $WindowHandle,
        [string] $Path
    )

    [OjNexusUiSmokeNative]::ActivateWindow($WindowHandle)
    Start-Sleep -Milliseconds 250
    $bounds = [OjNexusUiSmokeNative]::GetWindowBounds($WindowHandle)
    $screenshotWidth = $bounds[2] - $bounds[0]
    $screenshotHeight = $bounds[3] - $bounds[1]
    if ($screenshotWidth -le 0 -or $screenshotHeight -le 0) {
        throw "Window bounds are invalid: $($bounds -join ',')"
    }

    $bitmap = [Drawing.Bitmap]::new($screenshotWidth, $screenshotHeight)
    try {
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $captureMethod = $null
            try {
                $graphics.CopyFromScreen(
                    [Drawing.Point]::new($bounds[0], $bounds[1]),
                    [Drawing.Point]::new(0, 0),
                    $bitmap.Size,
                    [Drawing.CopyPixelOperation]::SourceCopy)
                $captureMethod = 'screen'
            }
            catch {
                $deviceContext = $graphics.GetHdc()
                try {
                    if (-not [OjNexusUiSmokeNative]::CaptureWindow($WindowHandle, $deviceContext)) {
                        throw "PrintWindow failed for handle $WindowHandle."
                    }
                    $captureMethod = 'print-window'
                }
                finally {
                    $graphics.ReleaseHdc($deviceContext)
                }
            }

            $samplePoints = @(
                [Drawing.Point]::new([int]($screenshotWidth * 0.25), [int]($screenshotHeight * 0.25)),
                [Drawing.Point]::new([int]($screenshotWidth * 0.50), [int]($screenshotHeight * 0.50)),
                [Drawing.Point]::new([int]($screenshotWidth * 0.75), [int]($screenshotHeight * 0.75)))
            $darkSamples = @($samplePoints | Where-Object {
                $pixel = $bitmap.GetPixel($_.X, $_.Y)
                ($pixel.R -lt 80 -and $pixel.G -lt 100 -and $pixel.B -lt 130)
            })
            if ($darkSamples.Count -eq 0) {
                throw "Screenshot capture produced no dark NEXUS client pixels (method: $captureMethod)."
            }
            $bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
            return $captureMethod
        }
        finally {
            $graphics.Dispose()
        }
    }
    finally {
        $bitmap.Dispose()
    }
}

function Try-Save-WindowScreenshot {
    param(
        [IntPtr] $WindowHandle,
        [string] $Path
    )

    try {
        $method = Save-WindowScreenshot -WindowHandle $WindowHandle -Path $Path
        Write-Host "SCREENSHOT: $([IO.Path]::GetFileName($Path)) / $method"
        return $true
    }
    catch {
        Write-Warning "SCREENSHOT SKIPPED: $([IO.Path]::GetFileName($Path)): $($_.Exception.Message)"
        return $false
    }
}

$uiDataDirectory = Join-Path ([IO.Path]::GetTempPath()) "ojnexus-ui-smoke-$([Guid]::NewGuid().ToString('N'))"
$process = $null
$oldDataDirectory = [Environment]::GetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', 'Process')

try {
    New-Item -ItemType Directory -Path $uiDataDirectory -Force | Out-Null
    [Environment]::SetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', $uiDataDirectory, 'Process')
    Write-Host "DESKTOP PATH: $DesktopPath"
    Write-Host "UI WINDOW: ${Width}x${Height}"
    Write-Host "UI OUTPUT: $OutputDirectory"

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $DesktopPath
    $startInfo.WorkingDirectory = Split-Path -Parent $DesktopPath
    $startInfo.UseShellExecute = $false
    $startInfo.Environment['OJ_NEXUS_DATA_DIRECTORY'] = $uiDataDirectory
    $process = [Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Desktop process failed to start: $DesktopPath"
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if ($process.HasExited) {
        throw "Desktop process exited before UI appeared with code $($process.ExitCode)."
    }
    if ($process.MainWindowHandle -eq [IntPtr]::Zero) {
        throw 'Desktop window did not appear within 15 seconds.'
    }

    $windowHandle = $process.MainWindowHandle
    [OjNexusUiSmokeNative]::ResizeAndShow($windowHandle, $Width, $Height)
    $root = [System.Windows.Automation.AutomationElement]::FromHandle($windowHandle)
    $screenshotCount = 0
    if ($null -eq $root) {
        throw 'Could not create a UI Automation root for the desktop window.'
    }

    foreach ($navigationName in @('DASHBOARD', 'CONNECTORS', 'SYNC HISTORY')) {
        [void](Require-DescendantByName -Root $root -Name $navigationName)
    }
    if (Try-Save-WindowScreenshot -WindowHandle $windowHandle -Path (Join-Path $OutputDirectory 'dashboard.png')) {
        $screenshotCount++
    }
    Write-Host 'DASHBOARD: RENDERED'

    Invoke-ButtonByName -Root $root -Name 'CONNECTORS'
    [void](Wait-ForElement -Root $root -Name 'PUBLIC HANDLES')
    $editCondition = [System.Windows.Automation.PropertyCondition]::new(
        [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
        [System.Windows.Automation.ControlType]::Edit)
    $editCount = $root.FindAll([System.Windows.Automation.TreeScope]::Descendants, $editCondition).Count
    if ($editCount -lt 3) {
        throw "Expected at least 3 public handle editors, found $editCount."
    }
    foreach ($judgeName in @('CODEFORCES PUBLIC HANDLE', 'ATCODER PUBLIC HANDLE', 'LUOGU PUBLIC HANDLE')) {
        [void](Require-DescendantByName -Root $root -Name $judgeName)
    }
    if (Try-Save-WindowScreenshot -WindowHandle $windowHandle -Path (Join-Path $OutputDirectory 'connectors.png')) {
        $screenshotCount++
    }
    Write-Host "CONNECTORS: RENDERED / EDITORS=$editCount"

    Invoke-ButtonByName -Root $root -Name 'SYNC HISTORY'
    [void](Wait-ForElement -Root $root -Name 'RECENT OPERATIONS')
    $comboCondition = [System.Windows.Automation.PropertyCondition]::new(
        [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
        [System.Windows.Automation.ControlType]::ComboBox)
    $comboCount = $root.FindAll([System.Windows.Automation.TreeScope]::Descendants, $comboCondition).Count
    if ($comboCount -lt 1) {
        throw 'Expected a history judge filter ComboBox.'
    }
    if (Try-Save-WindowScreenshot -WindowHandle $windowHandle -Path (Join-Path $OutputDirectory 'history.png')) {
        $screenshotCount++
    }
    Write-Host "SYNC HISTORY: RENDERED / FILTERS=$comboCount"
    Write-Host "SCREENSHOTS: $screenshotCount/3"
    Write-Host 'UI SMOKE: PASS'
}
finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit(5000) | Out-Null
        }
        $process.Dispose()
    }
    [Environment]::SetEnvironmentVariable('OJ_NEXUS_DATA_DIRECTORY', $oldDataDirectory, 'Process')
    if (Test-Path -LiteralPath $uiDataDirectory -PathType Container) {
        Remove-Item -LiteralPath $uiDataDirectory -Recurse -Force
    }
}
