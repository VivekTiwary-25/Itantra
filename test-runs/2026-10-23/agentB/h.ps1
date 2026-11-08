# Agent B helpers (copied from foreman h.ps1, restricted to Agent B phones only)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.chmod777.itantra'
$ph = @{ RMX3392='adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp'; RMX5070='adb-a83e3811-XIQIEi._adb-tls-connect._tcp' }
$root = 'D:\projects\sih\iTantra\test-runs\2026-10-23\agentB'
function Sh($n,$c){ & $adb -s $ph[$n] shell $c 2>&1 | Out-String }
function Snap($n,$name){ $d="$root\shots\$n"; New-Item -ItemType Directory -Force $d | Out-Null; cmd /c "`"$adb`" -s $($ph[$n]) exec-out screencap -p > `"$d\$name.png`""; "$d\$name.png" }
function Ui($n){ $null = & $adb -s $ph[$n] shell uiautomator dump /sdcard/ui_agentB.xml 2>&1; $t = & $adb -s $ph[$n] exec-out cat /sdcard/ui_agentB.xml; [xml]($t -join "`n") }
function Texts($n){ $x = Ui $n; $x.SelectNodes('//node') | ? { ($_.text -or $_.'content-desc') } | % { $b=[regex]::Matches($_.bounds,'\d+'); $cx=([int]$b[0].Value+[int]$b[2].Value)/2; $cy=([int]$b[1].Value+[int]$b[3].Value)/2; "{0}|{1}|({2},{3})|clk={4}|chk={5}" -f $_.text,$_.'content-desc',$cx,$cy,$_.clickable,$_.checked } }
function Tap($n,$x,$y){ $null = Sh $n "input tap $x $y" }
function TapText($n,$re){ $x = Ui $n; $m = $x.SelectNodes('//node') | ? { ($_.text -match $re) -or ($_.'content-desc' -match $re) } | select -First 1; if(-not $m){ return $false }; $b=[regex]::Matches($m.bounds,'\d+'); Tap $n (([int]$b[0].Value+[int]$b[2].Value)/2) (([int]$b[1].Value+[int]$b[3].Value)/2); $true }
function Back($n){ $null = Sh $n 'input keyevent 4' }
# Launch WITHOUT force-stop (do not kill a running app)
function Launch($n){ $null = Sh $n "monkey -p $pkg -c android.intent.category.LAUNCHER 1"; Start-Sleep 4 }
