param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$Python = 'python',
    [string]$BuildDirectory,
    [string]$OutputDirectory,
    [string]$FfmpegAar
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$pins = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'webp-16k.json') | ConvertFrom-Json
if (-not $SdkRoot) { throw 'Pass -SdkRoot with Android NDK 28.2.13676358 and CMake 3.22.1 installed.' }
if (-not $BuildDirectory) { $BuildDirectory = Join-Path $repoRoot 'build/native-webp' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repoRoot 'native/webp-1.6.0' }
$BuildDirectory = [IO.Path]::GetFullPath($BuildDirectory)
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$ndkRoot = Join-Path $SdkRoot ('ndk/' + $pins.build.ndkVersion)
$cmake = Join-Path $SdkRoot ('cmake/' + $pins.build.cmakeVersion + '/bin/cmake.exe')
$ninja = Join-Path $SdkRoot ('cmake/' + $pins.build.cmakeVersion + '/bin/ninja.exe')
$toolchain = Join-Path $ndkRoot 'build/cmake/android.toolchain.cmake'
$strip = Join-Path $ndkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
foreach ($required in @($cmake, $ninja, $toolchain, $strip)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing required build tool: $required" }
}
$ndkProperties = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ndkRoot 'source.properties')
if ($ndkProperties -notmatch ('Pkg.Revision\s*=\s*' + [regex]::Escape($pins.build.ndkVersion) + '(?:\s|$)')) {
    throw 'The installed NDK does not match the pinned revision.'
}
New-Item -ItemType Directory -Path $BuildDirectory -Force | Out-Null

function Get-VerifiedInput([string]$Url, [string]$Path, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Output "Downloading pinned input: $Url"
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Path
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant() -ne $Sha256) {
        throw "SHA-256 mismatch; the input was not used: $Path"
    }
}

$archive = Join-Path $BuildDirectory 'libwebp-1.6.0.tar.gz'
Get-VerifiedInput $pins.source.url $archive $pins.source.sha256
if (-not $FfmpegAar) { $FfmpegAar = Join-Path $BuildDirectory 'ffmpeg-0.18.1.aar' }
Get-VerifiedInput $pins.upstreamAar.url $FfmpegAar $pins.upstreamAar.sha256
& $Python (Join-Path $PSScriptRoot 'verify_webp_16k.py') extract --archive $archive --directory $BuildDirectory
if ($LASTEXITCODE -ne 0) { throw 'Pinned source extraction failed.' }
$sourceDirectory = Join-Path $BuildDirectory 'libwebp-1.6.0'
$sourceUnix = $sourceDirectory.Replace('\', '/')
$staging = Join-Path $BuildDirectory 'verified-libraries'
foreach ($abi in $pins.build.abis) {
    $abiBuild = Join-Path $BuildDirectory ('cmake-' + $abi)
    $arguments = @('-S', $sourceDirectory, '-B', $abiBuild, '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$ninja", "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
        "-DANDROID_ABI=$abi", "-DANDROID_PLATFORM=android-$($pins.build.androidApi)",
        '-DANDROID_STL=none', '-DCMAKE_BUILD_TYPE=Release', '-DBUILD_SHARED_LIBS=ON',
        '-DCMAKE_PLATFORM_NO_VERSIONED_SONAME=ON', '-DCMAKE_SKIP_RPATH=ON',
        "-DCMAKE_SHARED_LINKER_FLAGS=$($pins.build.linkerFlags)",
        "-DCMAKE_C_FLAGS=-ffile-prefix-map=$sourceUnix=libwebp-1.6.0",
        '-DWEBP_ENABLE_SIMD=ON', '-DWEBP_USE_THREAD=ON', '-DWEBP_ENABLE_SWAP_16BIT_CSP=ON',
        '-DWEBP_BUILD_LIBWEBPMUX=ON', '-DWEBP_BUILD_ANIM_UTILS=OFF', '-DWEBP_BUILD_CWEBP=OFF',
        '-DWEBP_BUILD_DWEBP=OFF', '-DWEBP_BUILD_GIF2WEBP=OFF', '-DWEBP_BUILD_IMG2WEBP=OFF',
        '-DWEBP_BUILD_VWEBP=OFF', '-DWEBP_BUILD_WEBPINFO=OFF', '-DWEBP_BUILD_WEBPMUX=OFF',
        '-DWEBP_BUILD_EXTRAS=OFF', '-DWEBP_BUILD_FUZZTEST=OFF')
    & $cmake @arguments
    if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed: $abi" }
    & $cmake --build $abiBuild --parallel 4 --target sharpyuv webpdecoder webp webpdemux libwebpmux
    if ($LASTEXITCODE -ne 0) { throw "WebP compilation failed: $abi" }
    $destination = Join-Path $staging $abi
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    foreach ($name in $pins.build.libraryNames) {
        $built = Join-Path $abiBuild $name
        if (-not (Test-Path -LiteralPath $built)) { throw "Expected shared library missing: $built" }
        $stagedLibrary = Join-Path $destination $name
        Copy-Item -LiteralPath $built -Destination $stagedLibrary -Force
        & $strip --strip-unneeded $stagedLibrary
        if ($LASTEXITCODE -ne 0) { throw "Stripping debug symbols failed: $abi/$name" }
    }
}
& $Python (Join-Path $PSScriptRoot 'verify_webp_16k.py') verify --pins (Join-Path $PSScriptRoot 'webp-16k.json') --aar $FfmpegAar --libraries $staging --output $OutputDirectory --source $sourceDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native compatibility verification failed; release libraries were not replaced.' }
Write-Output "Verified libraries and source notices: $OutputDirectory"
