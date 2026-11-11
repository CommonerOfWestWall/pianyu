$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
& (Join-Path $here 'build-android.ps1')
if ($LASTEXITCODE -ne 0) { throw 'base Android build failed' }
$out = Join-Path $here 'build/android-arm64-v2'
$engine = Join-Path (Resolve-Path (Join-Path $here '../engine-practice/SpektraLab/engine')).Path ''
$overlay = Join-Path $out 'android-overlay-vulkan_gpu.cpp'
$text = Get-Content (Join-Path $engine 'src/gpu/vulkan_gpu.cpp') -Raw
$gate = 'if (zero && !exact && properties_.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) {'
if (([regex]::Matches($text, [regex]::Escape($gate))).Count -ne 1) { throw 'expected one unfused-FMA gate' }
$text = $text.Replace($gate,'if (zero && !exact && true /* verified Android unfused GPU */) {')
if (([regex]::Matches($text, [regex]::Escape('probe_cpu_storage_range(detail);'))).Count -ne 1) { throw 'expected one desktop storage probe call' }
$text = $text.Replace('probe_cpu_storage_range(detail);','/* Android retains the advertised storage limit; no desktop capacity probe. */')
$text = $text.Replace('Vulkan probe passed on a CPU device with unfused fma','Vulkan probe passed on an Android GPU with unfused fma')
Set-Content -LiteralPath $overlay -Value $text -NoNewline
$ndk='C:/android_sdk/ndk/28.2.13676358'; $clang=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'; $sys=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/sysroot'; $b=Join-Path $out 'engine'
$inc = @("-I$engine/include","-I$engine/src","-I$engine/src/core","-I$engine/third_party/volk","-I$(Join-Path $PSScriptRoot '../deps/Vulkan-Headers')")
$obj=Join-Path $b 'CMakeFiles/spektralab_engine_shared.dir/src/gpu/vulkan_gpu.cpp.o'
& $clang '--target=aarch64-none-linux-android24' "--sysroot=$sys" '-Dspektralab_engine_shared_EXPORTS' @inc '-DANDROID' '-D_LIBCPP_ENABLE_EXPERIMENTAL' '-O3' '-DNDEBUG' '-fPIC' '-pthread' '-std=gnu++20' '-c' '-o' $obj $overlay
if ($LASTEXITCODE -ne 0) { throw 'overlay object compile failed' }
$objs=Get-ChildItem "$b/CMakeFiles/spektralab_pipeline.dir/src/pipeline/*.o" | ForEach-Object FullName
& $clang '--target=aarch64-none-linux-android24' "--sysroot=$sys" '-fPIC' '-DANDROID' '-D_LIBCPP_ENABLE_EXPERIMENTAL' '-O3' '-DNDEBUG' '-shared' '-Wl,--gc-sections' '-Wl,-soname,spektralab_engine.so' '-o' (Join-Path $b 'spektralab_engine.so') @objs $obj (Join-Path $b 'libspektralab_core.a') (Join-Path $b 'libspk_volk.a') '-pthread' '-ldl' '-latomic' '-lm'
if ($LASTEXITCODE -ne 0) { throw 'overlay link failed' }
Copy-Item (Join-Path $b 'spektralab_engine.so') (Join-Path $here 'spektralab_engine.android-overlay.so') -Force
Write-Host 'Built experimental Android unfused-FMA overlay.'
