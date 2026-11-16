# Install the debug APK, force the full splash, record the emulator, pull the clip.
#   powershell -ExecutionPolicy Bypass -File design/splash/capture_run.ps1
$ErrorActionPreference = "Stop"
$adb = "D:\Android\Sdk\platform-tools\adb.exe"
$root = "D:\test\KotlinMusicPlayer"
$apk = Join-Path $root "app\build\outputs\apk\debug\LHHBase_debug_v1.0.apk"
$pkg = "com.example.musicplayer"

& $adb install -r $apk | Out-Null
& $adb shell input keyevent KEYCODE_BACK | Out-Null
& $adb shell run-as $pkg rm -f shared_prefs/splash.xml
& $adb shell am force-stop $pkg
& $adb shell rm -f /sdcard/splash.mp4
& $adb logcat -c

$proc = Start-Process -FilePath $adb `
  -ArgumentList 'shell','screenrecord','--time-limit','8','--bit-rate','16000000','/sdcard/splash.mp4' `
  -PassThru -NoNewWindow
Start-Sleep -Milliseconds 900
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 9
if (-not $proc.HasExited) { $proc.WaitForExit(5000) }

& $adb pull /sdcard/splash.mp4 (Join-Path $root "design\splash\capture.mp4") | Out-Null
& $adb logcat -d -s SplashTiming AndroidRuntime | Select-Object -Last 8
Write-Output "clip ok"
