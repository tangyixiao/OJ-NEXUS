[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $Version = '0.1.0',
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [ValidateSet('win-x64')]
    [string] $Runtime = 'win-x64',
    [string] $OutputDirectory,
    [switch] $SkipUiSmoke
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

$packageName = "OJ-NEXUS-Windows-v$Version-win-x64"
$packageRoot = Join-Path $outputRoot $packageName
$archiveName = "$packageName.zip"
$archivePath = Join-Path $outputRoot $archiveName
$workRoot = Join-Path $outputRoot ".package-work-$([Guid]::NewGuid().ToString('N'))"
$stagingRoot = Join-Path $workRoot $packageName
$verificationRoot = Join-Path $workRoot 'verification'
$templatePath = Join-Path $repoRoot 'windows\PACKAGE_README.txt'
$verifierPath = Join-Path $repoRoot 'windows\scripts\verify-package.ps1'
$smokePath = Join-Path $repoRoot 'windows\scripts\smoke.ps1'
$uiSmokePath = Join-Path $repoRoot 'windows\scripts\ui-smoke.ps1'
$cliProject = Join-Path $repoRoot 'windows\src\OjNexus.Windows.Cli\OjNexus.Windows.Cli.csproj'
$desktopProject = Join-Path $repoRoot 'windows\src\OjNexus.Windows.Desktop\OjNexus.Windows.Desktop.csproj'
$cliPath = Join-Path $stagingRoot 'cli\ojnexus.exe'
$desktopPath = Join-Path $stagingRoot 'desktop\OjNexus.Windows.Desktop.exe'

foreach ($requiredPath in @($templatePath, $verifierPath, $smokePath, $cliProject, $desktopProject)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required packaging input is missing: $requiredPath"
    }
}
if (-not $SkipUiSmoke -and -not (Test-Path -LiteralPath $uiSmokePath -PathType Leaf)) {
    throw "UI smoke script is missing: $uiSmokePath"
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
if (Test-Path -LiteralPath $packageRoot) {
    Remove-Item -LiteralPath $packageRoot -Recurse -Force
}
if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}
New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null

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

function Invoke-CheckedPowerShell {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ScriptPath,
        [Parameter(Mandatory = $true)]
        [hashtable] $ParameterMap,
        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    & $ScriptPath @ParameterMap
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

try {
    $publishArguments = @(
        'publish',
        '--configuration', $Configuration,
        '--runtime', $Runtime,
        '--self-contained', 'true',
        '--no-restore',
        '-p:PublishSingleFile=false',
        '-p:DebugType=None')
    Invoke-CheckedNative -FilePath 'dotnet' -ArgumentList ($publishArguments + @('--output', (Join-Path $stagingRoot 'cli'), $cliProject)) -Description 'CLI publish'
    Invoke-CheckedNative -FilePath 'dotnet' -ArgumentList ($publishArguments + @('--output', (Join-Path $stagingRoot 'desktop'), $desktopProject)) -Description 'Desktop publish'

    $readme = (Get-Content -LiteralPath $templatePath -Raw).Replace('@VERSION@', $Version)
    Set-Content -LiteralPath (Join-Path $stagingRoot 'README.txt') -Value $readme -Encoding utf8NoBOM -NoNewline

    $manifestPath = Join-Path $stagingRoot 'SHA256SUMS.txt'
    $stagingPrefix = $stagingRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $manifestLines = Get-ChildItem -LiteralPath $stagingRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
        $relativePath = $_.FullName.Substring($stagingPrefix.Length).Replace([IO.Path]::DirectorySeparatorChar, '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
        "$hash  $relativePath"
    }
    Set-Content -LiteralPath $manifestPath -Value $manifestLines -Encoding utf8NoBOM

    Invoke-CheckedPowerShell -ScriptPath $verifierPath -ParameterMap @{ PackageRoot = $stagingRoot; ExpectedVersion = $Version } -Description 'Staged package verification'
    New-Item -ItemType Directory -Path $verificationRoot -Force | Out-Null
    Invoke-CheckedPowerShell -ScriptPath $smokePath -ParameterMap @{ Configuration = $Configuration; CliPath = $cliPath; DesktopPath = $desktopPath } -Description 'Packaged CLI/WPF smoke'
    if (-not $SkipUiSmoke) {
        Invoke-CheckedPowerShell -ScriptPath $uiSmokePath -ParameterMap @{ Configuration = $Configuration; DesktopPath = $desktopPath; OutputDirectory = (Join-Path $verificationRoot 'ui-smoke') } -Description 'Packaged UI smoke'
    }

    $workArchivePath = Join-Path $workRoot $archiveName
    Compress-Archive -Path $stagingRoot -DestinationPath $workArchivePath -CompressionLevel Optimal
    $extractRoot = Join-Path $verificationRoot 'archive-extract'
    Expand-Archive -LiteralPath $workArchivePath -DestinationPath $extractRoot -Force
    $topLevelEntries = @(Get-ChildItem -LiteralPath $extractRoot -Force)
    if ($topLevelEntries.Count -ne 1 -or $topLevelEntries[0].Name -ne $packageName -or -not $topLevelEntries[0].PSIsContainer) {
        throw "Archive must contain exactly one top-level directory named $packageName."
    }
    Invoke-CheckedPowerShell -ScriptPath $verifierPath -ParameterMap @{ PackageRoot = (Join-Path $extractRoot $packageName); ExpectedVersion = $Version } -Description 'Extracted archive verification'

    Move-Item -LiteralPath $stagingRoot -Destination $packageRoot
    Move-Item -LiteralPath $workArchivePath -Destination $archivePath
    $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToUpperInvariant()
    $packageFileCount = @(Get-ChildItem -LiteralPath $packageRoot -File -Recurse).Count
    Write-Host "PACKAGE ROOT: $packageRoot"
    Write-Host "PACKAGE FILES: $packageFileCount"
    Write-Host "PACKAGE ZIP: $archivePath"
    Write-Host "PACKAGE ZIP SHA256: $archiveHash"
}
finally {
    if (Test-Path -LiteralPath $workRoot -PathType Container) {
        Remove-Item -LiteralPath $workRoot -Recurse -Force
    }
}
