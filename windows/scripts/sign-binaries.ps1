[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]] $FilePaths,
    [string] $SigntoolPath,
    [string] $CertThumbprint,
    [string] $TimestampUrl = 'http://timestamp.digicert.com'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Certificate material NEVER lives in this repository. The signing cert is referenced by
# thumbprint only and must already exist in the machine's user or local-machine certificate
# store (imported out of band). The thumbprint travels through an environment variable, not
# through any committed file.

if ([string]::IsNullOrWhiteSpace($SigntoolPath)) {
    $SigntoolPath = $env:OJ_NEXUS_SIGNTOOL_PATH
}
if ([string]::IsNullOrWhiteSpace($CertThumbprint)) {
    $CertThumbprint = $env:OJ_NEXUS_SIGNING_CERT_THUMBPRINT
}

if ([string]::IsNullOrWhiteSpace($SigntoolPath)) {
    # Best-effort discovery of the Windows SDK signtool, newest version first.
    $kitRoot = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    if (Test-Path -LiteralPath $kitRoot -PathType Container) {
        $candidate = Get-ChildItem -LiteralPath $kitRoot -Directory |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'x64\signtool.exe' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
        if ($candidate) {
            $SigntoolPath = $candidate
        }
    }
}

if ([string]::IsNullOrWhiteSpace($SigntoolPath)) {
    throw 'signtool.exe was not found. Install the Windows SDK or set OJ_NEXUS_SIGNTOOL_PATH.'
}
if (-not (Test-Path -LiteralPath $SigntoolPath -PathType Leaf)) {
    throw "Configured signtool path does not exist: $SigntoolPath"
}
if ([string]::IsNullOrWhiteSpace($CertThumbprint)) {
    throw 'Signing certificate thumbprint is required (OJ_NEXUS_SIGNING_CERT_THUMBPRINT).'
}

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [Parameter(Mandatory = $true)]
        [string[]] $ArgumentList,
        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

foreach ($file in $FilePaths) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Signing target is missing: $file"
    }

    Invoke-CheckedNative -FilePath $SigntoolPath -ArgumentList @(
        'sign', '/fd', 'SHA256', '/sha1', $CertThumbprint, '/tr', $TimestampUrl, '/td', 'SHA256', $file) -Description "signtool sign $file"
    Invoke-CheckedNative -FilePath $SigntoolPath -ArgumentList @('verify', '/pa', '/v', $file) -Description "signtool verify $file"
}

Write-Host "SIGNING: APPLIED to $($FilePaths.Count) file(s)"
