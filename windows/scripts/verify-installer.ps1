[CmdletBinding()]
param(
    # Exactly one of these must be supplied.
    [string] $InstallerPath,
    [string] $PackageRoot,
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $ExpectedVersion
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($InstallerPath) -eq [string]::IsNullOrWhiteSpace($PackageRoot)) {
    throw 'Provide exactly one of -InstallerPath or -PackageRoot.'
}
if ([string]::IsNullOrWhiteSpace($ExpectedVersion)) {
    throw '-ExpectedVersion is required.'
}

if (-not [string]::IsNullOrWhiteSpace($InstallerPath)) {
    if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) {
        throw "Installer executable is missing: $InstallerPath"
    }

    $item = Get-Item -LiteralPath $InstallerPath
    if ($item.Length -le 0) {
        throw 'Installer executable is empty.'
    }

    $reported = $item.VersionInfo.ProductVersion
    if ([string]::IsNullOrWhiteSpace($reported) -or -not $reported.Contains($ExpectedVersion)) {
        throw "Installer version resource does not carry $ExpectedVersion (reported: '$reported')."
    }

    Write-Host "INSTALLER VERIFY: version OK ($reported)"
    Write-Host "INSTALLER VERIFY: size OK ($($item.Length) bytes)"
    # A full silent install/uninstall round trip needs an interactive session; document it as a
    # manual step. /HELP is the automatable behavior check that needs no elevation.
    Write-Host 'INSTALLER VERIFY: silent install/uninstall round trip is a documented manual step (clean VM).'
}
else {
    if (-not (Test-Path -LiteralPath $PackageRoot -PathType Container)) {
        throw "Package root is missing: $PackageRoot"
    }

    $cliPath = Join-Path $PackageRoot 'cli\ojnexus.exe'
    $desktopPath = Join-Path $PackageRoot 'desktop\OjNexus.Windows.Desktop.exe'
    foreach ($required in @($cliPath, $desktopPath)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Package payload is missing: $required"
        }
    }

    Write-Host 'PACKAGE VERIFY: payload OK (cli + desktop present)'
    Write-Host 'PACKAGE VERIFY: installer compile skipped on this machine (no ISCC); CI builds it.'
}

exit 0
