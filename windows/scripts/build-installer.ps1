[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $Version = '0.1.0',
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [ValidateSet('win-x64')]
    [string] $Runtime = 'win-x64',
    [string] $OutputDirectory,
    [switch] $SkipUiSmoke,
    # Skip the installer compile step (used when ISCC is unavailable, e.g. dev boxes).
    [switch] $SkipInstaller
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactsRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'windows\artifacts'))
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $artifactsRoot 'self-contained'
}
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$artifactsPrefix = $artifactsRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $outputRoot.StartsWith($artifactsPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must be inside $artifactsRoot."
}

$packageScript = Join-Path $repoRoot 'windows\scripts\package.ps1'
$installerScript = Join-Path $repoRoot 'windows\scripts\installer.iss'
$verifyInstallerScript = Join-Path $repoRoot 'windows\scripts\verify-installer.ps1'
foreach ($requiredPath in @($packageScript, $installerScript, $verifyInstallerScript)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required installer input is missing: $requiredPath"
    }
}

function Remove-InstallerPath {
    <#
    Same contract as package.ps1's Remove-PackagePath: delete through the .NET file APIs and
    verify the real filesystem state afterwards, so guarded-host false reports fail loudly.
    #>
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    if (Test-Path -LiteralPath $Path -PathType Container) {
        [IO.Directory]::Delete($Path, $true)
    }
    else {
        [IO.File]::Delete($Path)
    }

    if (Test-Path -LiteralPath $Path) {
        throw "Failed to remove $Path."
    }
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

# 1. Produce the deterministic package (publishes CLI + WPF, runs smokes, writes SHA256SUMS).
& $packageScript -Version $Version -Configuration $Configuration -Runtime $Runtime -OutputDirectory $outputRoot -SkipUiSmoke:$SkipUiSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Packaging failed with exit code $LASTEXITCODE."
}

$packageName = "OJ-NEXUS-Windows-v$Version-win-x64"
$packageRoot = Join-Path $outputRoot $packageName

# 2. Compile the installer when ISCC is available.
$isccCandidates = @(
    (Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe'),
    (Join-Path $env:ProgramFiles 'Inno Setup 6\ISCC.exe')
)
$isccPath = $isccCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $isccPath -and (Get-Command iscc -ErrorAction SilentlyContinue)) {
    $isccPath = (Get-Command iscc).Source
}

$installerExe = $null
if ($SkipInstaller) {
    Write-Host 'INSTALLER: SKIPPED (-SkipInstaller)'
}
elseif (-not $isccPath) {
    throw 'Inno Setup 6 (ISCC.exe) was not found. Install Inno Setup 6 or pass -SkipInstaller.'
}
else {
    $installerWorkRoot = Join-Path $outputRoot ".installer-work-$([Guid]::NewGuid().ToString('N'))"
    $installerOutputRoot = Join-Path $installerWorkRoot 'installer-output'
    New-Item -ItemType Directory -Path $installerOutputRoot -Force | Out-Null
    try {
        Invoke-CheckedNative -FilePath $isccPath -ArgumentList @(
            "/DMyAppVersion=$Version",
            "/DSourceRoot=$packageRoot",
            "/O$installerOutputRoot",
            $installerScript) -Description 'Inno Setup compile'

        $produced = @(Get-ChildItem -LiteralPath $installerOutputRoot -File -Filter 'OJ-NEXUS-Setup-*.exe')
        if ($produced.Count -ne 1) {
            throw 'Inno Setup did not produce exactly one installer executable.'
        }
        $installerExe = Join-Path $outputRoot $produced[0].Name
        Move-Item -LiteralPath $produced[0].FullName -Destination $installerExe

        # 3. Optional signing — strictly opt-in via environment; never fails the unsigned build.
        $signtoolPath = $env:OJ_NEXUS_SIGNTOOL_PATH
        $certThumbprint = $env:OJ_NEXUS_SIGNING_CERT_THUMBPRINT
        if (-not [string]::IsNullOrWhiteSpace($signtoolPath) -and -not [string]::IsNullOrWhiteSpace($certThumbprint)) {
            Invoke-CheckedNative -FilePath $signtoolPath -ArgumentList @(
                'sign', '/fd', 'SHA256', '/sha1', $certThumbprint, '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', $installerExe) -Description 'signtool sign'
            Invoke-CheckedNative -FilePath $signtoolPath -ArgumentList @('verify', '/pa', $installerExe) -Description 'signtool verify'
            Write-Host 'SIGNING: APPLIED'
        }
        else {
            Write-Host 'SIGNING: SKIPPED (no cert configured)'
        }
    }
    finally {
        if (Test-Path -LiteralPath $installerWorkRoot -PathType Container) {
            try {
                Remove-InstallerPath -Path $installerWorkRoot
            }
            catch {
                Write-Warning "Could not remove installer work directory: $installerWorkRoot"
            }
        }
    }
}

# 4. Verify whatever we produced.
if ($installerExe) {
    & $verifyInstallerScript -InstallerPath $installerExe -ExpectedVersion $Version
    if ($LASTEXITCODE -ne 0) {
        throw 'Installer verification failed.'
    }
    Write-Host "INSTALLER: $installerExe"
}
else {
    & $verifyInstallerScript -PackageRoot $packageRoot -ExpectedVersion $Version
    if ($LASTEXITCODE -ne 0) {
        throw 'Package verification failed.'
    }
}

Write-Host "INSTALLER BUILD: OK (version $Version)"
