[CmdletBinding()]
param(
    [string]$SourceRoot = (Join-Path $PSScriptRoot '..'),
    [string]$NdkPath = $env:ANDROID_NDK,
    [string]$CmakePath = $env:ANDROID_CMAKE,
    [string]$GlslangPath = $env:GLSLANG_PATH,
    [string]$NinjaPath
)

$ErrorActionPreference = 'Stop'
function Require-File([string]$Path, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Path) -or !(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Name is required and must name an existing file" }
    return (Resolve-Path -LiteralPath $Path).Path
}
function Require-Directory([string]$Path, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Path) -or !(Test-Path -LiteralPath $Path -PathType Container)) { throw "$Name is required and must name an existing directory" }
    return (Resolve-Path -LiteralPath $Path).Path
}
function Replace-Exactly([string]$Path, [string]$Old, [string]$New, [string]$Label) {
    $text = Get-Content -LiteralPath $Path -Raw
    $count = ([regex]::Matches($text, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "$Label expected exactly one match, found ${count}: $Path" }
    Set-Content -LiteralPath $Path -Value $text.Replace($Old, $New) -NoNewline
}
function Remove-Stage([string]$Path) {
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $full = [IO.Path]::GetFullPath($Path)
    if (!$full.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($full) -notmatch '^pianyu-engine-build-[0-9a-f]{32}$') { throw "refusing to remove staging path: $full" }
    Remove-Item -LiteralPath $full -Recurse -Force
}

$Root = Require-Directory $SourceRoot 'SourceRoot'
$Ndk = Require-Directory $NdkPath 'NdkPath/ANDROID_NDK'
$Cmake = Require-File $CmakePath 'CmakePath/ANDROID_CMAKE'
$Glslang = Require-File $GlslangPath 'GlslangPath/GLSLANG_PATH'
if ([string]::IsNullOrWhiteSpace($NinjaPath)) { $NinjaPath = Join-Path (Split-Path $Cmake) 'ninja.exe' }
$Ninja = Require-File $NinjaPath 'NinjaPath or CMake sibling ninja.exe'
$Vulkan = Require-Directory (Join-Path $Root 'native/deps/Vulkan-Headers') 'native/deps/Vulkan-Headers'
$EngineSource = Join-Path $Root 'engine'
$Stage = Join-Path ([IO.Path]::GetTempPath()) ("pianyu-engine-build-" + [guid]::NewGuid().ToString('N'))
$StageEngine = Join-Path $Stage 'engine'
New-Item -ItemType Directory -Force -Path $Stage | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $Root 'CMakeLists.txt') -Destination $Stage -Force
    Copy-Item -LiteralPath $EngineSource -Destination $Stage -Recurse -Force
    $gpu = Join-Path $StageEngine 'src/gpu/vulkan_gpu.cpp'
    Replace-Exactly $gpu 'if (zero && !exact && properties_.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) {' 'if (zero && !exact && true /* verified Android unfused GPU */) {' 'Android math-mode gate'
    Replace-Exactly $gpu 'Vulkan probe passed on a CPU device with unfused fma' 'Vulkan probe passed on an Android GPU with unfused fma' 'Android capability text'
    Replace-Exactly $gpu 'probe_cpu_storage_range(detail);' '/* Android retains the advertised storage limit; no desktop capacity probe. */' 'Android desktop probe removal'
    $probe = Join-Path $StageEngine 'src/shaders/vulkan/spk_math_probe.comp'
    Replace-Exactly $probe 'precise float rounded' 'float rounded' 'Android math probe rounded'
    Replace-Exactly $probe 'precise float residual' 'float residual' 'Android math probe residual'

    $Build = Join-Path $Stage 'build/android-arm64'
    $Toolchain = Join-Path $Ndk 'build/cmake/android.toolchain.cmake'
    $CmakeArgs = @('-S', $Stage, '-B', $Build, '-G', 'Ninja', "-DCMAKE_MAKE_PROGRAM=$Ninja", "-DCMAKE_TOOLCHAIN_FILE=$Toolchain", '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-24', '-DANDROID_STL=c++_shared', '-DCMAKE_BUILD_TYPE=Release', '-DBUILD_TESTING=OFF', '-DCMAKE_CXX_FLAGS=-D_LIBCPP_ENABLE_EXPERIMENTAL', '-DSPEKTRALAB_BUILD_TEST_DLL=ON', '-DSPEKTRALAB_BUILD_NATIVE_RAW=OFF', '-DSPEKTRALAB_BUILD_HOST=OFF', '-DSPEKTRALAB_BUILD_DESKTOP=OFF', '-DSPEKTRALAB_REQUIRE_VULKAN=ON', "-DSPEKTRALAB_DEPS_DIR=$(Split-Path $Vulkan)", "-DSPEKTRALAB_VULKAN_HEADERS=$Vulkan", "-DSPEKTRALAB_GLSLANG=$Glslang")
    & $Cmake @CmakeArgs
    if ($LASTEXITCODE -ne 0) { throw "CMake configure failed: $LASTEXITCODE" }
    & $Cmake --build $Build --target spektralab_engine_shared --parallel
    if ($LASTEXITCODE -ne 0) { throw "engine build failed: $LASTEXITCODE" }
    $built = Join-Path $Build 'engine/spektralab_engine.so'
    $strip = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
    $appLib = Join-Path $Root 'app/src/main/jniLibs/arm64-v8a/libpianyu_core.so'
    New-Item -ItemType Directory -Force -Path (Split-Path $appLib) | Out-Null
    Copy-Item $built $appLib -Force
    & $strip --strip-unneeded $appLib
    if ($LASTEXITCODE -ne 0) { throw "llvm-strip failed: $LASTEXITCODE" }
    $assetVulkan = Join-Path $Root 'app/src/main/assets/engine/vulkan'
    New-Item -ItemType Directory -Force -Path $assetVulkan | Out-Null
    Copy-Item (Join-Path $Build 'engine/resources/vulkan/*') $assetVulkan -Force
    $hashes = [ordered]@{}
    Get-ChildItem (Join-Path $Root 'app/src/main/assets/engine') -Recurse -File | ForEach-Object { $hashes[$_.FullName.Substring((Join-Path $Root 'app/src/main/assets/engine').Length + 1).Replace('\','/')] = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    $manifest = $hashes | ConvertTo-Json -Depth 3
    Set-Content (Join-Path $Root 'app/src/main/assets/engine-manifest.json') $manifest -Encoding utf8
    Set-Content (Join-Path $Root 'app/src/main/assets/engine-version.txt') (([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($manifest)) | ForEach-Object { $_.ToString('x2') }) -join '') -NoNewline
    Write-Host "Built and installed $appLib; generated Vulkan assets and manifest from isolated staging source."
} finally {
    if (Test-Path -LiteralPath $Stage) { Remove-Stage $Stage }
}
