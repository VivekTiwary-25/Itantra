# Requires a live `adb logcat ... > $Log` capture already running (the device log buffer rotates).
# Drives DebugTtsReceiver on the connected phone: ml x10 twice, then en/hi regression.
param([string]$Log = "tts-research\ml-android\warm_and_regression_logcat.log")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
function Returned { (Select-String -Path $Log -Pattern "debug speak returned" | Measure-Object).Count }
function Speak($lang, $text, $n) {
    $b = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($text))
    & $adb shell "am broadcast -n com.chmod777.itantra/.DebugTtsReceiver --es lang $lang --es text_b64 '$b'" | Out-Null
    for ($i = 0; $i -lt 90; $i++) { if ((Returned) -ge $n) { break }; Start-Sleep -Milliseconds 500 }
    Write-Host "done $n ($lang)"
}
$ml = Get-Content "tts-research\inputs\ml.txt" -Encoding UTF8
$n = 0
foreach ($pass in 1, 2) { foreach ($s in $ml) { $n++; Speak "ml" $s $n } }
$reg = @(@("en","this is a test message"), @("hi","à¤¯à¤¹ à¤à¤• à¤ªà¤°à¥€à¤•à¥à¤·à¤£ à¤¸à¤‚à¤¦à¥‡à¤¶ à¤¹à¥ˆ"),
         @("en","Water is rising near the school. Move to safe ground."), @("hi","à¤¸à¥à¤•à¥‚à¤² à¤•à¥‡ à¤ªà¤¾à¤¸ à¤ªà¤¾à¤¨à¥€ à¤¬à¤¢à¤¼ à¤°à¤¹à¤¾ à¤¹à¥ˆ"))
foreach ($r in $reg) { $n++; Speak $r[0] $r[1] $n }
Write-Host "FINISHED"

