[CmdletBinding()]
param(
    [string]$Python = "python",
    [string]$OutputPath,
    [string]$SourceDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if (-not $OutputPath) {
    $OutputPath = Join-Path $RepositoryRoot "app\src\main\res\raw\ytdlp"
}
$OutputPath = [IO.Path]::GetFullPath($OutputPath)

$BaseRepository = "https://github.com/yt-dlp/yt-dlp.git"
$BaseCommit = "3a08beaf031ab68f966401ead017ac81fe8486cf"
$BaseVersion = "2026.08.19"
$Patch1 = Join-Path $PSScriptRoot "patches\yt-dlp-2026.08.19-douyin-abogus.patch"
$Patch2 = Join-Path $PSScriptRoot "patches\yt-dlp-flowframe-douyin-uploader.patch"
$Patch3 = Join-Path $PSScriptRoot "patches\yt-dlp-flowframe-douyin-gallery.patch"
$Patch1Sha256 = "F6EF427BA0E7282694A109D06CA3C51773D676C5AA2B0348ED20639B509FFA5B"
$Patch2Sha256 = "B311CF8BCC3B3CA756CC305CB8853BB1EF0F4541EBD63085E50EAA616DAE2B07"
$Patch3Sha256 = "1CCEE6377AF996D3928724C6F6B0BE00B1EAF937E9FFDCEC19BB74DA9AEB79D9"
$Manifest = Join-Path $PSScriptRoot "yt-dlp-kernel.json"
$Builder = Join-Path $PSScriptRoot "build_ytdlp_zipapp.py"

function Assert-LastExitCode {
    param([string]$Operation)
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE"
    }
}

function Assert-FileHash {
    param([string]$Path, [string]$Expected)
    $Actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
    if ($Actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path. Expected $Expected, got $Actual"
    }
}

Assert-FileHash -Path $Patch1 -Expected $Patch1Sha256
Assert-FileHash -Path $Patch2 -Expected $Patch2Sha256
Assert-FileHash -Path $Patch3 -Expected $Patch3Sha256

$TemporaryRoot = $null
if ($SourceDirectory) {
    $Source = [IO.Path]::GetFullPath($SourceDirectory)
} else {
    $TemporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $TemporaryRoot = Join-Path $TemporaryBase ("flowframe-ytdlp-build-" + [Guid]::NewGuid().ToString("N"))
    $TemporaryRoot = [IO.Path]::GetFullPath($TemporaryRoot)
    if (-not $TemporaryRoot.StartsWith($TemporaryBase, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to create a work directory outside the system temporary directory"
    }
    New-Item -ItemType Directory -Path $TemporaryRoot | Out-Null
    $Source = Join-Path $TemporaryRoot "source"

    & git init --quiet $Source
    Assert-LastExitCode "git init"
    & git -C $Source remote add upstream $BaseRepository
    Assert-LastExitCode "git remote add"
    & git -C $Source fetch --quiet --depth 1 upstream $BaseCommit
    Assert-LastExitCode "git fetch"
    & git -C $Source checkout --quiet --detach FETCH_HEAD
    Assert-LastExitCode "git checkout"
}

try {
    $Head = (& git -C $Source rev-parse HEAD).Trim()
    Assert-LastExitCode "git rev-parse"
    if ($Head -ne $BaseCommit) {
        throw "Source commit mismatch. Expected $BaseCommit, got $Head"
    }

    $VersionFile = Join-Path $Source "yt_dlp\version.py"
    $VersionText = [IO.File]::ReadAllText($VersionFile, [Text.Encoding]::UTF8)
    if ($VersionText -notmatch "__version__\s*=\s*'$([Regex]::Escape($BaseVersion))'") {
        throw "Source version is not yt-dlp $BaseVersion"
    }

    & git -C $Source apply --check $Patch1
    Assert-LastExitCode "upstream-derived patch validation"
    & git -C $Source apply $Patch1
    Assert-LastExitCode "upstream-derived patch application"
    & git -C $Source apply --check $Patch2
    Assert-LastExitCode "FlowFrame compatibility patch validation"
    & git -C $Source apply $Patch2
    Assert-LastExitCode "FlowFrame compatibility patch application"
    & git -C $Source apply --check $Patch3
    Assert-LastExitCode "FlowFrame gallery patch validation"
    & git -C $Source apply $Patch3
    Assert-LastExitCode "FlowFrame gallery patch application"

    Push-Location $Source
    try {
        & $Python "devscripts/make_lazy_extractors.py" "yt_dlp/extractor/lazy_extractors.py"
        Assert-LastExitCode "lazy extractor generation"
    } finally {
        Pop-Location
    }

    & $Python $Builder --source $Source --output $OutputPath --manifest $Manifest
    Assert-LastExitCode "deterministic zipapp build"

    $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $OutputPath).Hash
    $Length = (Get-Item -LiteralPath $OutputPath).Length
    Write-Output "Built: $OutputPath"
    Write-Output "Bytes: $Length"
    Write-Output "SHA256: $Hash"
} finally {
    if ($TemporaryRoot -and (Test-Path -LiteralPath $TemporaryRoot)) {
        $TemporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $ResolvedTemporaryRoot = [IO.Path]::GetFullPath($TemporaryRoot)
        if (-not $ResolvedTemporaryRoot.StartsWith($TemporaryBase, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove a work directory outside the system temporary directory"
        }
        Remove-Item -LiteralPath $ResolvedTemporaryRoot -Recurse -Force
    }
}
