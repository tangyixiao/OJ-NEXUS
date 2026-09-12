[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $PackageRoot,
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $ExpectedVersion
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $PackageRoot -ErrorAction Stop).Path)
$rootPrefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$manifestPath = Join-Path $root 'SHA256SUMS.txt'

if (-not [string]::IsNullOrWhiteSpace($ExpectedVersion)) {
    $expectedName = "OJ-NEXUS-Windows-v$ExpectedVersion-win-x64"
    if ((Split-Path -Leaf $root) -ne $expectedName) {
        throw "Unexpected package directory name. Expected $expectedName."
    }
}

$requiredRelativePaths = @(
    'cli/ojnexus.exe',
    'cli/coreclr.dll',
    'desktop/OjNexus.Windows.Desktop.exe',
    'desktop/coreclr.dll',
    'README.txt',
    'SHA256SUMS.txt')
foreach ($relativePath in $requiredRelativePaths) {
    $requiredPath = Join-Path $root ($relativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Package member is missing: $relativePath"
    }
}

$manifestEntries = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($line in (Get-Content -LiteralPath $manifestPath)) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    $parts = $line -split '\s+', 2
    if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9A-Fa-f]{64}$') {
        throw "Invalid SHA256 manifest entry."
    }

    $rawRelativePath = $parts[1].Trim()
    $hasDriveRoot = $rawRelativePath -match '^[A-Za-z]:[\\/]'
    if ([string]::IsNullOrWhiteSpace($rawRelativePath) -or [IO.Path]::IsPathRooted($rawRelativePath) -or $hasDriveRoot) {
        throw "Manifest contains an invalid path."
    }

    $normalizedRelativePath = $rawRelativePath.Replace('\', '/').TrimStart('/')
    $segments = $normalizedRelativePath -split '/'
    $hasUnsafeSegment = @($segments | Where-Object { [string]::IsNullOrWhiteSpace($_) -or $_ -eq '.' -or $_ -eq '..' }).Count -gt 0
    if ($segments.Count -eq 0 -or $hasUnsafeSegment) {
        throw "Manifest contains an unsafe path."
    }

    $candidatePath = [IO.Path]::GetFullPath((Join-Path $root ($normalizedRelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)))
    if (-not $candidatePath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest path escapes the package root."
    }
    if ($normalizedRelativePath -ieq 'SHA256SUMS.txt') {
        throw "SHA256SUMS.txt must not list itself."
    }
    if ($manifestEntries.ContainsKey($normalizedRelativePath)) {
        throw "Manifest contains a duplicate path: $normalizedRelativePath"
    }
    if (-not (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
        throw "Manifest references a missing file: $normalizedRelativePath"
    }

    $manifestEntries.Add($normalizedRelativePath, $parts[0].ToUpperInvariant())
}

if ($manifestEntries.Count -eq 0) {
    throw 'SHA256 manifest is empty.'
}

foreach ($entry in $manifestEntries.GetEnumerator()) {
    $filePath = Join-Path $root ($entry.Key -replace '/', [IO.Path]::DirectorySeparatorChar)
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash
    if ($actualHash -ine $entry.Value) {
        throw "SHA256 mismatch: $($entry.Key)"
    }
}

$packageFiles = Get-ChildItem -LiteralPath $root -File -Recurse | ForEach-Object {
    $_.FullName.Substring($rootPrefix.Length).Replace([IO.Path]::DirectorySeparatorChar, '/')
}
$unlistedFiles = @($packageFiles | Where-Object {
    $_ -ine 'SHA256SUMS.txt' -and -not $manifestEntries.ContainsKey($_)
})
if ($unlistedFiles.Count -gt 0) {
    throw "Package files missing from SHA256 manifest: $($unlistedFiles -join ', ')"
}

Write-Host "PACKAGE ROOT: $root"
Write-Host "PACKAGE FILES VERIFIED: $($manifestEntries.Count)"
