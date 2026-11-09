$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_ADB) { $env:ANDROID_ADB } else { 'C:/android_sdk/platform-tools/adb.exe' }
$remote = if ($args.Count -gt 0) { $args[0] } else { '/data/local/tmp/spektralab-native' }
$here = $PSScriptRoot
& $adb get-state
if ($LASTEXITCODE -ne 0) { throw 'adb device is not ready' }
& $adb shell "mkdir -p $remote/resources/vulkan"
& $adb push (Join-Path $here 'spektralab_engine.so') "$remote/spektralab_engine.so"
& $adb push (Join-Path $here 'libc++_shared.so') "$remote/libc++_shared.so"
& $adb push (Join-Path $here 'spk_android_driver') "$remote/spk_android_driver"
& $adb push (Join-Path $here 'samples/kodim01_1024.f32') "$remote/kodim01_1024.f32"
& $adb push (Join-Path $here 'samples/kodim01_1024.json') "$remote/kodim01_1024.json"
& $adb push (Join-Path $here 'samples/kodim01_2048.f32') "$remote/kodim01_2048.f32"
& $adb push (Join-Path $here 'samples/kodim01_2048.json') "$remote/kodim01_2048.json"
& $adb shell "mkdir -p $remote/resources"
Get-ChildItem (Join-Path $here 'resources') -Recurse -File | ForEach-Object {
  $relative = $_.FullName.Substring((Join-Path $here 'resources').Length + 1).Replace('\','/')
  $parent = Split-Path $relative -Parent
  if ($parent) { & $adb shell "mkdir -p $remote/resources/$($parent.Replace('\','/'))" }
  & $adb push $_.FullName "$remote/resources/$relative"
  if ($LASTEXITCODE -ne 0) { throw "failed to push resource $relative" }
}
& $adb shell "chmod 755 $remote/spk_android_driver"
Write-Host "Prepared $remote; this script does not execute the driver."
