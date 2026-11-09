$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Source = Join-Path $Root 'engine-practice/SpektraLab'
$Engine = Join-Path $Source 'engine'
$Out = Join-Path $PSScriptRoot 'build/android-arm64-v2'
$Ndk = if ($env:ANDROID_NDK) { $env:ANDROID_NDK } else { 'C:/android_sdk/ndk/28.2.13676358' }
$Cmake = if ($env:ANDROID_CMAKE) { $env:ANDROID_CMAKE } else { 'C:/android_sdk/cmake/3.22.1/bin/cmake.exe' }
$Ninja = Join-Path (Split-Path $Cmake) 'ninja.exe'
$Glslang = Join-Path $Root 'engine-practice/deps/glslang/bin/glslang.exe'
$Vulkan = Join-Path $Root 'engine-practice/deps/Vulkan-Headers'
$Toolchain = Join-Path $Ndk 'build/cmake/android.toolchain.cmake'

New-Item -ItemType Directory -Force -Path $Out | Out-Null
if (Test-Path $Out) {
  $rootFull = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\') + '\'
  $outFull = [IO.Path]::GetFullPath($Out).TrimEnd('\') + '\'
  if (!$outFull.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) { throw "refusing to remove build path outside native-mobile: $outFull" }
  Remove-Item -LiteralPath $Out -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null
& $Cmake -S $Source -B $Out -G Ninja `
  "-DCMAKE_MAKE_PROGRAM=$Ninja" "-DCMAKE_TOOLCHAIN_FILE=$Toolchain" `
  '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-24' `
  '-DCMAKE_BUILD_TYPE=Release' '-DBUILD_TESTING=OFF' `
  '-DCMAKE_CXX_FLAGS=-D_LIBCPP_ENABLE_EXPERIMENTAL' `
  '-DSPEKTRALAB_BUILD_TEST_DLL=ON' '-DSPEKTRALAB_BUILD_NATIVE_RAW=OFF' `
  '-DSPEKTRALAB_BUILD_HOST=OFF' '-DSPEKTRALAB_BUILD_DESKTOP=OFF' `
  '-DSPEKTRALAB_REQUIRE_VULKAN=ON' "-DSPEKTRALAB_DEPS_DIR=$(Split-Path $Vulkan)" `
  "-DSPEKTRALAB_VULKAN_HEADERS=$Vulkan" "-DSPEKTRALAB_GLSLANG=$Glslang"
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed with exit code $LASTEXITCODE" }
& $Cmake --build $Out --target spektralab_engine_shared --parallel
if ($LASTEXITCODE -ne 0) { throw "Android engine build failed with exit code $LASTEXITCODE" }

# Android/Adreno follows the SPIR-V NoContraction decoration emitted by the
# upstream `precise` probe and reports an unfused GPU FMA. Keep the upstream
# source untouched; compile a local Android-only probe overlay without the
# qualifiers so the device reports its actual native FMA behavior.
$ProbeSource = Join-Path $Out 'spk_math_probe_android.comp'
$ProbeText = Get-Content (Join-Path $Engine 'src/shaders/vulkan/spk_math_probe.comp') -Raw
$ProbeText = $ProbeText.Replace('precise float rounded', 'float rounded').Replace('precise float residual', 'float residual')
Set-Content -LiteralPath $ProbeSource -Value $ProbeText -NoNewline
& $Glslang -V --target-env vulkan1.0 "-I$(Join-Path $Engine 'src/shaders/vulkan')" -o (Join-Path $Out 'engine/resources/vulkan/spk_math_probe.spv') $ProbeSource
if ($LASTEXITCODE -ne 0) { throw "Android math probe overlay compilation failed with exit code $LASTEXITCODE" }

$Clang = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
$Driver = Join-Path $PSScriptRoot 'spk_android_driver.cpp'
$DriverExe = Join-Path $Out 'spk_android_driver'
& $Clang --target=aarch64-linux-android24 -std=c++20 -O2 -fno-fast-math -fPIC `
  '-D_LIBCPP_ENABLE_EXPERIMENTAL' -I(Join-Path $Engine 'include') $Driver `
  (Join-Path $Out 'engine/spektralab_engine.so') '-Wl,-rpath,$ORIGIN' -o $DriverExe
if ($LASTEXITCODE -ne 0) { throw "Android driver link failed with exit code $LASTEXITCODE" }

$Resources = Join-Path $Out 'engine/resources'
$PublishedResources = Join-Path $PSScriptRoot 'resources'
New-Item -ItemType Directory -Force -Path $PublishedResources | Out-Null
# Overlay-copy only: old files are retained so this script never performs an
# unvalidated recursive delete. The CMake resource tree is authoritative.
Copy-Item -Recurse (Join-Path $Resources '*') $PublishedResources -Force
Copy-Item (Join-Path $Out 'engine/spektralab_engine.so') (Join-Path $PSScriptRoot 'spektralab_engine.so') -Force
Copy-Item $DriverExe (Join-Path $PSScriptRoot 'spk_android_driver') -Force
$Libcxx = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so'
Copy-Item $Libcxx (Join-Path $PSScriptRoot 'libc++_shared.so') -Force
Write-Host "Built Android arm64 artifacts in $PSScriptRoot"
