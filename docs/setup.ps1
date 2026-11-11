<#
.SYNOPSIS
    shangOS setup wizard for Windows (PowerShell 5.1 or 7). Same steps as setup.sh on Mac and Linux.

.DESCRIPTION
    One line, in PowerShell (no administrator rights needed):

        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; iex ((New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1'))

    The wizard
      1. finds your TV (adb devices, wireless-debugging discovery, saved addresses, or type one)
      2. checks it (Android 12+, a TV, what is installed now)
      3. asks which build and what to switch on
      4. shows the exact commands and asks once
      5. downloads the signed release from github.com/madushas/shangos-launcher, verifies its
         SHA-256, installs it, grants the optional permissions, makes it the home screen

    Every command that changes the TV is printed first (dim lines starting with +). The script only
    talks to github.com/madushas/shangos-launcher and to your TV; the one exception is winget,
    scoop or Chocolatey, if you let it install adb. No telemetry. The APK sits in a temporary
    folder that is deleted on exit. What it remembers (addresses you save, how to undo Home) is in
    %APPDATA%\shangos.

    With options (the one-liner form that takes them):

        & ([scriptblock]::Create((New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1'))) -Tv 192.168.1.50:5555 -Variant offline -Yes

    To read it first:

        (New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1') | Set-Content setup.ps1
        notepad setup.ps1
        powershell -ExecutionPolicy Bypass -File .\setup.ps1

.PARAMETER Tv         The TV's adb address (192.168.1.50 or 192.168.1.50:5555).
.PARAMETER Variant    release (default) or offline (no internet permission at all).
.PARAMETER Release    Install this release (v0.4.0) instead of the latest.
.PARAMETER Apk        Install this APK instead of downloading a release.
.PARAMETER Pair       HOST:PORT of a TV showing a pairing code (Wireless debugging).
.PARAMETER Code       The six-digit pairing code (asked for when left out).
.PARAMETER Yes        No questions: take every default (needs -Tv unless one TV is connected).
.PARAMETER NoHome     Do not make shangOS the home screen.
.PARAMETER DisableStock  Only if the TV keeps opening its own home: switch that launcher off (never the setup app). Some Google TVs cannot restart without it and loop, so it asks first.
.PARAMETER NoWidgets  Do not grant the optional permissions.
.PARAMETER NoAot      Skip the ahead-of-time compile.
.PARAMETER Saver      Also use the shangOS screen saver.
.PARAMETER BlockAds   Also turn on ad and tracker blocking for the whole TV (Private DNS, AdGuard DNS).
.PARAMETER Dns        Only change the TV's Private DNS: adguard, your own hostname, or off (back to automatic).
.PARAMETER Status     Show what is installed on the TV and what Home opens.
.PARAMETER Uninstall  Remove shangOS (Home goes back to the TV's own launcher first).
.PARAMETER Plain      No colours, no menus: plain text.
.PARAMETER NoColor    Keep the menus, drop the colours (NO_COLOR works too).
#>
param(
    [string]$Tv = '',
    [ValidateSet('release', 'offline')][string]$Variant = 'release',
    [string]$Release = '',
    [string]$Apk = '',
    [string]$Pair = '',
    [string]$Code = '',
    [switch]$Yes,
    [switch]$NoHome,
    [switch]$DisableStock,
    [switch]$NoWidgets,
    [switch]$NoAot,
    [switch]$Saver,
    [switch]$BlockAds,
    [string]$Dns = '',
    [switch]$Status,
    [switch]$Uninstall,
    [switch]$Plain,
    [switch]$NoColor,
    [switch]$Version,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$script:SetupVersion = '2.1'
$script:Pkg = 'dev.madusha.shangos'
$script:HomeAct = "$($script:Pkg)/.MainActivity"
$script:Ambient = "$($script:Pkg)/$($script:Pkg).AmbientDream"
$script:Repo = 'madushas/shangos-launcher'
$script:Known = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.google.android.tungsten.setupwraith')
# Only these may be switched off, and only with -DisableStock. Never the setup app: some Google TVs cannot start without it.
$script:Disablable = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher')
$script:MinSdk = 31
$script:ConfigDir = Join-Path $env:APPDATA 'shangos'
$script:Endpoints = Join-Path $script:ConfigDir 'endpoints'
$script:Device = ''
$script:Adb = 'adb'
$script:Tmp = ''
$script:Changed = $false
$script:Rc = 0
$script:Choice = 0

if ($Version) { Write-Output "shangOS setup $($script:SetupVersion)"; return }
if ($Help) {
    Write-Output @'
shangOS setup wizard (Windows). With no options it opens the guided wizard.
  -Tv ADDRESS    the TV's adb address (192.168.1.50 or 192.168.1.50:5555)
  -Variant NAME  release (default) or offline (no internet permission at all)
  -Release TAG   install this release (v0.4.0) instead of the latest
  -Apk PATH      install this APK instead of downloading a release
  -Pair HOST:PORT  pair with a TV that shows a pairing code; -Code NNNNNN is the code
  -Yes           no questions: take every default
  -NoHome  -NoWidgets  -NoAot  -Saver   switch single steps off or on
  -DisableStock   only if the TV keeps opening its own home: switch that launcher off (it asks; never the setup app)
  -BlockAds      also turn on ad and tracker blocking for the whole TV (Private DNS)
  -Dns CHOICE    only change the TV's Private DNS: adguard, your own hostname, or off
  -Status        show what is installed and what Home opens
  -Uninstall     remove shangOS (Home goes back to the TV's own launcher first)
  -Plain  -NoColor   plain text / no colours
Full text: https://shangos.madusha.dev/setup/
'@
    return
}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if ($Apk -and $Release) { Write-Error '-Apk and -Release do not go together'; return }
if ($Release -and $Release -notmatch '^[vV]') { $Release = "v$Release" }
$DoHome = -not $NoHome
$DoWidgets = -not $NoWidgets
$DoAot = -not $NoAot
$DoSaver = [bool]$Saver
$DoDns = [bool]$BlockAds

# ---- Terminal -------------------------------------------------------------------------
# Rich mode needs a real console (not redirected, not the ISE) and ANSI support. Anything else
# runs on parameters with plain text.
$script:UI = $false
$script:Esc = [string][char]27
function Enable-Vt {
    try {
        if (-not ('Shangos.Win32' -as [type])) {
            Add-Type -Namespace Shangos -Name Win32 -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern IntPtr GetStdHandle(int h);
[DllImport("kernel32.dll")] public static extern bool GetConsoleMode(IntPtr h, out int m);
[DllImport("kernel32.dll")] public static extern bool SetConsoleMode(IntPtr h, int m);
'@
        }
        $h = [Shangos.Win32]::GetStdHandle(-11)
        $m = 0
        if (-not [Shangos.Win32]::GetConsoleMode($h, [ref]$m)) { return $false }
        if (($m -band 4) -ne 0) { return $true }
        return [Shangos.Win32]::SetConsoleMode($h, ($m -bor 4))
    }
    catch { return $false }
}
if (-not $Plain) {
    try {
        $redirected = [Console]::IsInputRedirected -or [Console]::IsOutputRedirected
        if (-not $redirected -and $Host.Name -notmatch 'ISE' -and $Host.Name -ne 'Default Host') {
            $script:UI = (Enable-Vt)
        }
    }
    catch { $script:UI = $false }
}
$useColor = $script:UI -and (-not $NoColor) -and (-not $env:NO_COLOR)
$script:R = ''; $script:B = ''; $script:D = ''; $script:Inv = ''; $script:Acc = ''; $script:Ok = ''; $script:Wn = ''; $script:Er = ''
if ($useColor) {
    $e = $script:Esc
    $script:R = "$e[0m"; $script:B = "$e[1m"; $script:D = "$e[2m"; $script:Inv = "$e[7m"
    $script:Ok = "$e[32m"; $script:Wn = "$e[33m"; $script:Er = "$e[31m"; $script:Acc = "$e[38;5;105m"
}
# Unicode only where the font is known to cope (Windows Terminal, VS Code, PowerShell 7 hosts).
$uni = [bool]($env:WT_SESSION -or $env:TERM_PROGRAM -or $env:ConEmuANSI -or ($PSVersionTable.PSVersion.Major -ge 7))
if ($uni) {
    try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { $uni = $false }
}
function Ch([int]$n) { [string][char]$n }
if ($uni) {
    $script:GOk = Ch 0x2713; $script:GEr = Ch 0x2717; $script:GWn = '!'; $script:GOn = Ch 0x25CF; $script:GOff = Ch 0x25CB
    $script:GPtr = Ch 0x203A; $script:GFull = Ch 0x2588; $script:GEmpty = Ch 0x2591; $script:GH = Ch 0x2500; $script:GV = Ch 0x2502
    $script:GTL = Ch 0x256D; $script:GTR = Ch 0x256E; $script:GBL = Ch 0x2570; $script:GBR = Ch 0x256F
    $script:GChk = Ch 0x25FC; $script:GUnchk = Ch 0x25FB; $script:Ell = Ch 0x2026
    $script:Spin = @(0x280B, 0x2819, 0x2839, 0x2838, 0x283C, 0x2834, 0x2826, 0x2827, 0x2807, 0x280F | ForEach-Object { Ch $_ })
}
else {
    $script:GOk = 'v'; $script:GEr = 'x'; $script:GWn = '!'; $script:GOn = '*'; $script:GOff = 'o'
    $script:GPtr = '>'; $script:GFull = '#'; $script:GEmpty = '-'; $script:GH = '-'; $script:GV = '|'
    $script:GTL = '+'; $script:GTR = '+'; $script:GBL = '+'; $script:GBR = '+'
    $script:GChk = '[x]'; $script:GUnchk = '[ ]'; $script:Ell = '~'
    $script:Spin = @('|', '/', '-', '\')
}

function Out-Raw([string]$s) { [Console]::Out.Write($s) }
function Say([string]$s = '') { if ($script:UI) { Out-Raw "$s`n" } else { Write-Host $s } }
function Note([string]$s) { Say "$($script:D)  $s$($script:R)" }
function Good([string]$s) { Say "$($script:Ok)$($script:GOk)$($script:R) $s" }
function Warn([string]$s) { Say "$($script:Wn)$($script:GWn)$($script:R) $s" }
function Bad([string]$s) { Say "$($script:Er)$($script:GEr)$($script:R) $s" }
function CmdLine([string]$s) { Say "$($script:D)  + $s$($script:R)" }
function Die([string]$s) {
    CursorShow
    Bad "shangos setup: $s"
    throw [System.OperationCanceledException]::new($s)
}
function CursorHide { if ($script:UI) { Out-Raw "$($script:Esc)[?25l" } }
function CursorShow { if ($script:UI) { Out-Raw "$($script:Esc)[?25h" } }
function Cols {
    $w = 80
    try { $w = [Console]::WindowWidth } catch { $w = 80 }
    if ($w -lt 40) { $w = 40 }
    if ($w -gt 100) { $w = 100 }
    return $w
}
function Rep([string]$s, [int]$n) { if ($n -le 0) { return '' }; return ($s * $n) }
function Fit([string]$t, [int]$room = 4) {
    $w = Cols
    if ($t.Length -gt ($w - $room)) { return $t.Substring(0, [Math]::Max(1, $w - $room - 1)) + $script:Ell }
    return $t
}
function Wrap([string]$t, [int]$width) {
    $lines = @()
    if ([string]::IsNullOrEmpty($t)) { return @(' ') }
    $cur = ''
    foreach ($word in ($t -split ' ')) {
        if (($cur.Length + $word.Length + 1) -gt $width -and $cur.Length -gt 0) { $lines += $cur; $cur = $word }
        elseif ($cur.Length -eq 0) { $cur = $word }
        else { $cur = "$cur $word" }
    }
    $lines += $cur
    return $lines
}
function Box([string]$title, [string[]]$lines) {
    $w = [Math]::Min((Cols), 78)
    $inner = $w - 4
    Say "$($script:Acc)$($script:GTL)$($script:GH) $($script:B)$title$($script:R)$($script:Acc) $(Rep $script:GH ($inner - $title.Length - 2))$($script:GH)$($script:GTR)$($script:R)"
    foreach ($line in $lines) {
        foreach ($part in (Wrap $line $inner)) {
            Say "$($script:Acc)$($script:GV)$($script:R) $part$(Rep ' ' ($inner - $part.Length)) $($script:Acc)$($script:GV)$($script:R)"
        }
    }
    Say "$($script:Acc)$($script:GBL)$(Rep $script:GH ($inner + 2))$($script:GBR)$($script:R)"
}
function Banner {
    Say ''
    if ($script:UI) {
        Say "  $($script:Acc)$($script:B)$($script:GOn) shangOS$($script:R)  $($script:D)setup wizard $($script:SetupVersion)$($script:R)"
        Say "  $($script:D)A calm home screen for Android TV and Google TV$($script:R)"
    }
    else { Say "shangOS setup wizard $($script:SetupVersion)" }
    Say ''
}
$script:StepNames = @('TV', 'Build', 'Options', 'Review', 'Install')
function StepBar([int]$n) {
    if (-not $script:UI) { return }
    if ((Cols) -lt 62) { Say "$($script:D)  Step $n of $($script:StepNames.Count): $($script:StepNames[$n - 1])$($script:R)"; return }
    $s = ''
    for ($i = 1; $i -le $script:StepNames.Count; $i++) {
        $name = $script:StepNames[$i - 1]
        if ($i -lt $n) { $s += "$($script:Ok)$($script:GOk) $name$($script:R)" }
        elseif ($i -eq $n) { $s += "$($script:Acc)$($script:B)$($script:GOn) $name$($script:R)" }
        else { $s += "$($script:D)$($script:GOff) $name$($script:R)" }
        if ($i -lt $script:StepNames.Count) { $s += "$($script:D) $($script:GH)$($script:GH) $($script:R)" }
    }
    Say "  $s"
    Say ''
}

# ---- Keys and prompts -----------------------------------------------------------------
function ReadKey { [Console]::ReadKey($true) }
function Ask([string]$q, [string]$def = '') {
    $p = $q
    if ($def) { $p = "$q [$def]" }
    CursorShow
    $ans = Read-Host -Prompt "  $p"
    if (-not $ans -and $def) { $ans = $def }
    return $ans
}
# Menu: sets $script:Rc to 0 (chosen, index in $script:Choice), 1 (back: Esc) or 2 (quit: q).
$script:MenuDesc = @()
$script:MenuStart = 1
function Menu([string]$title, [string[]]$opts) {
    $n = $opts.Count
    $sel = $script:MenuStart
    $script:MenuStart = 1
    if (-not $script:UI) {
        Say ''
        Say $title
        for ($i = 1; $i -le $n; $i++) { Say ("  {0}) {1}" -f $i, $opts[$i - 1]) }
        while ($true) {
            $r = Ask "Choose 1-$n" ''
            if ($r -match '^[qQ]$') { $script:Rc = 2; return }
            $k = 0
            if ([int]::TryParse($r, [ref]$k) -and $k -ge 1 -and $k -le $n) { $script:Choice = $k; $script:Rc = 0; return }
            Say "Please type a number from 1 to $n."
        }
    }
    CursorHide
    $drawn = 0
    $buf = ''
    try {
        while ($true) {
            if ($drawn -gt 0) { Out-Raw "$($script:Esc)[${drawn}A" }
            Out-Raw "`r$($script:Esc)[K  $($script:B)$title$($script:R)`n"
            for ($i = 1; $i -le $n; $i++) {
                if ($i -eq $sel) { Out-Raw "`r$($script:Esc)[K  $($script:Acc)$($script:GPtr)$($script:R) $($script:Inv)$($script:B) $(Fit $opts[$i - 1] 10) $($script:R)`n" }
                else { Out-Raw "`r$($script:Esc)[K    $(Fit $opts[$i - 1] 10)`n" }
            }
            $desc = ''
            if ($script:MenuDesc.Count -ge $sel) { $desc = $script:MenuDesc[$sel - 1] }
            Out-Raw "`r$($script:Esc)[K  $($script:D)$(Fit $desc 6)$($script:R)`n"
            if ($buf) { $hint = "number ${buf}: Enter to choose" } else { $hint = "up/down move $($script:GH) Enter choose $($script:GH) Esc back $($script:GH) q quit" }
            Out-Raw "`r$($script:Esc)[K  $($script:D)$(Fit $hint 6)$($script:R)`n"
            $drawn = $n + 3
            $k = ReadKey
            switch ($k.Key) {
                'UpArrow' { $sel--; if ($sel -lt 1) { $sel = $n }; $buf = '' }
                'DownArrow' { $sel++; if ($sel -gt $n) { $sel = 1 }; $buf = '' }
                'Home' { $sel = 1 }
                'End' { $sel = $n }
                'Backspace' { if ($buf.Length -gt 0) { $buf = $buf.Substring(0, $buf.Length - 1) } }
                'Enter' {
                    if ($buf) {
                        $v = [int]$buf
                        if ($v -ge 1 -and $v -le $n) { $sel = $v } else { $buf = ''; continue }
                    }
                    $script:Choice = $sel
                    Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"
                    Out-Raw "  $($script:Ok)$($script:GOk)$($script:R) $title $($script:D)$($script:GPtr)$($script:R) $($script:B)$(Fit $opts[$sel - 1] ($title.Length + 10))$($script:R)`n"
                    $script:Rc = 0
                    return
                }
                'Escape' { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 1; return }
                'LeftArrow' { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 1; return }
                default {
                    $ch = $k.KeyChar
                    if ($ch -match '^[0-9]$' -and $buf.Length -lt 3) { $buf += $ch }
                    elseif ($ch -eq 'j') { $sel++; if ($sel -gt $n) { $sel = 1 } }
                    elseif ($ch -eq 'k') { $sel--; if ($sel -lt 1) { $sel = $n } }
                    elseif ($ch -eq 'q' -or $ch -eq 'Q') { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 2; return }
                }
            }
        }
    }
    finally { CursorShow }
}
# Checklist: $script:Chk (bool[]) preset by the caller. Space switches, a = all/none. Rc as Menu.
$script:Chk = @()
function Checklist([string]$title, [string[]]$opts) {
    $n = $opts.Count
    if (-not $script:UI) {
        for ($i = 0; $i -lt $n; $i++) { $script:Chk[$i] = (Confirm $opts[$i] $script:Chk[$i]) }
        $script:Rc = 0
        return
    }
    $sel = 1
    $drawn = 0
    CursorHide
    try {
        while ($true) {
            if ($drawn -gt 0) { Out-Raw "$($script:Esc)[${drawn}A" }
            Out-Raw "`r$($script:Esc)[K  $($script:B)$title$($script:R)`n"
            for ($i = 1; $i -le $n; $i++) {
                if ($script:Chk[$i - 1]) { $mark = $script:GChk; $col = $script:Ok } else { $mark = $script:GUnchk; $col = $script:D }
                if ($i -eq $sel) { Out-Raw "`r$($script:Esc)[K  $($script:Acc)$($script:GPtr)$($script:R) $col$mark$($script:R) $($script:B)$(Fit $opts[$i - 1] 12)$($script:R)`n" }
                else { Out-Raw "`r$($script:Esc)[K    $col$mark$($script:R) $(Fit $opts[$i - 1] 12)`n" }
            }
            $desc = ''
            if ($script:MenuDesc.Count -ge $sel) { $desc = $script:MenuDesc[$sel - 1] }
            Out-Raw "`r$($script:Esc)[K  $($script:D)$(Fit $desc 6)$($script:R)`n"
            Out-Raw "`r$($script:Esc)[K  $($script:D)$(Fit "up/down move $($script:GH) Space switch $($script:GH) a all/none $($script:GH) Enter continue $($script:GH) Esc back" 6)$($script:R)`n"
            $drawn = $n + 3
            $k = ReadKey
            switch ($k.Key) {
                'UpArrow' { $sel--; if ($sel -lt 1) { $sel = $n } }
                'DownArrow' { $sel++; if ($sel -gt $n) { $sel = 1 } }
                'Spacebar' { $script:Chk[$sel - 1] = -not $script:Chk[$sel - 1] }
                'RightArrow' { $script:Chk[$sel - 1] = -not $script:Chk[$sel - 1] }
                'Enter' {
                    Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"
                    $on = @()
                    for ($i = 0; $i -lt $n; $i++) { if ($script:Chk[$i]) { $on += ($opts[$i] -replace ' \(.*$', '') } }
                    $txt = 'nothing'
                    if ($on.Count -gt 0) { $txt = $on -join ', ' }
                    Out-Raw "  $($script:Ok)$($script:GOk)$($script:R) $title $($script:D)$($script:GPtr)$($script:R) $($script:B)$(Fit $txt ($title.Length + 10))$($script:R)`n"
                    $script:Rc = 0
                    return
                }
                'Escape' { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 1; return }
                'LeftArrow' { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 1; return }
                default {
                    $ch = $k.KeyChar
                    if ($ch -eq 'a' -or $ch -eq 'A') {
                        $allOn = $true
                        for ($i = 0; $i -lt $n; $i++) { if (-not $script:Chk[$i]) { $allOn = $false } }
                        for ($i = 0; $i -lt $n; $i++) { $script:Chk[$i] = -not $allOn }
                    }
                    elseif ($ch -eq 'q' -or $ch -eq 'Q') { Out-Raw "$($script:Esc)[${drawn}A$($script:Esc)[J"; $script:Rc = 2; return }
                }
            }
        }
    }
    finally { CursorShow }
}
# Confirm: $true for yes. -Yes answers the default without asking; without a console, same.
function Confirm([string]$q, [bool]$def = $false) {
    if ($Yes -or (-not $script:UI)) { return $def }
    $val = $def
    CursorHide
    try {
        while ($true) {
            if ($val) { Out-Raw "`r$($script:Esc)[K  $($script:B)$(Fit $q 30)$($script:R)  $($script:Inv)$($script:B) Yes $($script:R)  $($script:D)No$($script:R)   $($script:D)(y/n)$($script:R)" }
            else { Out-Raw "`r$($script:Esc)[K  $($script:B)$(Fit $q 30)$($script:R)  $($script:D)Yes$($script:R)  $($script:Inv)$($script:B) No $($script:R)   $($script:D)(y/n)$($script:R)" }
            $k = ReadKey
            $ch = $k.KeyChar
            if ($ch -eq 'y' -or $ch -eq 'Y') { $val = $true; break }
            if ($ch -eq 'n' -or $ch -eq 'N') { $val = $false; break }
            switch ($k.Key) {
                'LeftArrow' { $val = -not $val }
                'RightArrow' { $val = -not $val }
                'Tab' { $val = -not $val }
                'Spacebar' { $val = -not $val }
                'Enter' { break }
                'Escape' { $val = $false; break }
            }
            if ($k.Key -eq 'Enter' -or $k.Key -eq 'Escape') { break }
        }
    }
    finally { CursorShow }
    $picked = 'no'
    if ($val) { $picked = 'yes' }
    Out-Raw "`r$($script:Esc)[K  $($script:Ok)$($script:GOk)$($script:R) $(Fit $q 24) $($script:D)$($script:GPtr)$($script:R) $($script:B)$picked$($script:R)`n"
    return $val
}

# ---- Running programs -----------------------------------------------------------------
function Quote-Arg([string]$a) {
    if ($a -eq '') { return '""' }
    if ($a -notmatch '[\s"]') { return $a }
    return '"' + ($a -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}
# Runs a program, with a spinner when -Label is given. Returns @{ Code; Out } and never throws.
function Invoke-Native([string]$file, [string[]]$arguments, [string]$label = '', [int]$timeoutSec = 0) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $file
    $psi.Arguments = (($arguments | ForEach-Object { Quote-Arg $_ }) -join ' ')
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $result = @{ Code = 1; Out = '' }
    try { $p = [System.Diagnostics.Process]::Start($psi) }
    catch { $result.Out = $_.Exception.Message; return $result }
    $outTask = $p.StandardOutput.ReadToEndAsync()
    $errTask = $p.StandardError.ReadToEndAsync()
    $i = 0
    $start = Get-Date
    if ($label -and $script:UI) { CursorHide }
    while (-not $p.HasExited) {
        if ($label -and $script:UI) { Out-Raw "`r$($script:Esc)[K  $($script:Acc)$($script:Spin[$i % $script:Spin.Count])$($script:R) $(Fit $label 8)" }
        $i++
        Start-Sleep -Milliseconds 100
        if ($timeoutSec -gt 0 -and ((Get-Date) - $start).TotalSeconds -gt $timeoutSec) { try { $p.Kill() } catch {}; break }
    }
    $p.WaitForExit()
    $text = ($outTask.Result + $errTask.Result) -replace "`r", ''
    $result.Code = $p.ExitCode
    $result.Out = $text
    if ($label) {
        $secs = [int]((Get-Date) - $start).TotalSeconds
        $took = ''
        if ($secs -ge 2) { $took = " $($script:D)${secs}s$($script:R)" }
        if ($script:UI) {
            if ($p.ExitCode -eq 0) { Out-Raw "`r$($script:Esc)[K  $($script:Ok)$($script:GOk)$($script:R) $(Fit $label 14)$took`n" }
            else { Out-Raw "`r$($script:Esc)[K  $($script:Er)$($script:GEr)$($script:R) $(Fit $label 14)`n" }
            CursorShow
        }
        else { if ($p.ExitCode -eq 0) { Say "- $label`n  ok" } else { Say "- $label`n  failed" } }
    }
    return $result
}
$script:LastOut = ''
function ShowTail {
    $lines = @($script:LastOut -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 6)
    foreach ($l in $lines) { Say "$($script:D)    $(Fit $l 8)$($script:R)" }
}
# adb with the chosen TV. -Show prints the command first and marks the TV as changed.
function Adb([string[]]$a, [string]$label = '', [switch]$Mutates) {
    $full = @('-s', $script:Device) + $a
    if ($Mutates) { CmdLine ("adb " + ($full -join ' ')); $script:Changed = $true }
    $r = Invoke-Native $script:Adb $full $label
    $script:LastOut = $r.Out
    return $r
}
function Tv([string[]]$a) {
    $r = Invoke-Native $script:Adb (@('-s', $script:Device, 'shell') + $a)
    return ($r.Out.Trim())
}
function Prop([string]$name) { Tv @('getprop', $name) }
function HomeNow { $o = Tv @('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'); return (($o -split "`n") | Select-Object -Last 1).Trim() }
function IsInstalled([string]$pkg) { return ((Tv @('pm', 'list', 'packages', $pkg)) -split "`n" | ForEach-Object { $_.Trim() }) -contains "package:$pkg" }
function InstalledVersion { $o = Tv @('dumpsys', 'package', $script:Pkg); $m = [regex]::Match($o, '(?m)^\s*versionName=(\S+)'); if ($m.Success) { return $m.Groups[1].Value }; return '' }
function ShowHome { if ((Tv @('dumpsys', 'power')) -match 'mWakefulness=Awake') { [void](Tv @('input', 'keyevent', 'KEYCODE_HOME')) } }
function SerialTag { return ((Tv @('getprop', 'ro.serialno')) -replace '[^A-Za-z0-9]', '') }
function PrivateDir { if (-not (Test-Path $script:ConfigDir)) { [void](New-Item -ItemType Directory -Path $script:ConfigDir -Force) } }
function StateFile { PrivateDir; return (Join-Path $script:ConfigDir "home-$(SerialTag).txt") }
function SaverFile { PrivateDir; return (Join-Path $script:ConfigDir "ambient-$(SerialTag).txt") }
function DnsFile { PrivateDir; return (Join-Path $script:ConfigDir "dns-$(SerialTag).txt") }
function SavedEndpoints {
    if (Test-Path $script:Endpoints) { return @(Get-Content $script:Endpoints | Where-Object { $_ -match '^[A-Za-z0-9._-]+(:[0-9]{1,5})?$' }) }
    return @()
}
function ValidEndpoint([string]$e) { return ($e -match '^[A-Za-z0-9._-]+(:[0-9]{1,5})?$') }
function WithPort([string]$e) { if ($e -match ':') { return $e }; return "${e}:5555" }

# ---- adb ------------------------------------------------------------------------------
function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
        $candidates += (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\adb.exe')
        $pk = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
        if (Test-Path $pk) { $candidates += @(Get-ChildItem $pk -Filter 'Google.PlatformTools*' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'platform-tools\adb.exe' }) }
    }
    if ($env:USERPROFILE) { $candidates += (Join-Path $env:USERPROFILE 'scoop\shims\adb.exe') }
    if ($env:ChocolateyInstall) { $candidates += (Join-Path $env:ChocolateyInstall 'bin\adb.exe') }
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    return $null
}
function Refresh-Path {
    $m = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $u = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = (@($m, $u) | Where-Object { $_ }) -join ';'
}
function Ensure-Adb {
    $found = Find-Adb
    if (-not $found) {
        Warn 'adb (Android platform-tools) is not installed on this computer.'
        $installer = $null
        if (Get-Command winget -ErrorAction SilentlyContinue) { $installer = @('winget', 'install', '-e', '--id', 'Google.PlatformTools', '--accept-source-agreements', '--accept-package-agreements') }
        elseif (Get-Command scoop -ErrorAction SilentlyContinue) { $installer = @('scoop', 'install', 'adb') }
        elseif (Get-Command choco -ErrorAction SilentlyContinue) { $installer = @('choco', 'install', 'adb', '-y') }
        if ($installer -and $script:UI -and -not $Yes) {
            CmdLine ($installer -join ' ')
            if (Confirm 'Install it now?' $true) {
                & $installer[0] $installer[1..($installer.Count - 1)]
                Refresh-Path
                $found = Find-Adb
            }
        }
    }
    if (-not $found) { Die 'adb is needed. Install platform-tools (https://developer.android.com/tools/releases/platform-tools), or run: winget install -e --id Google.PlatformTools, then run this again.' }
    $script:Adb = $found
    # Start the adb server here, so its background process never holds a pipe of ours open.
    try { & $script:Adb start-server *> $null } catch {}
}
function Connect-Endpoint([string]$raw) {
    if (-not (ValidEndpoint $raw)) { Bad 'That does not look like an address (192.168.1.50 or 192.168.1.50:5555).'; return $false }
    $ep = WithPort $raw
    CmdLine "adb connect $ep"
    $r = Invoke-Native $script:Adb @('connect', $ep) 'Connecting' 20
    if ($r.Out -match 'connected to' -or $r.Out -match 'already connected') {
        $script:Device = $ep
        Good "Connected to $ep"
        if ((SavedEndpoints) -notcontains $ep -and -not $Yes -and $script:UI) {
            if (Confirm "Remember $ep for next time?" $false) { PrivateDir; Add-Content -Path $script:Endpoints -Value $ep }
        }
        return $true
    }
    Bad "Could not connect: $($r.Out.Trim())"
    Note 'On the TV: Settings, System, About, press Build 7 times, then Developer options, USB debugging (or Wireless debugging).'
    Note 'TVs that show a pairing code need: setup.ps1 -Pair HOST:PORT (or the Pair option in the wizard).'
    return $false
}
function Await-Authorized {
    for ($i = 0; $i -lt 60; $i++) {
        $r = Invoke-Native $script:Adb @('-s', $script:Device, 'get-state')
        if ($r.Out.Trim() -eq 'device') { if ($script:UI) { Out-Raw "`r$($script:Esc)[K" }; return $true }
        if ($i -eq 0) {
            Warn "Look at your TV: choose $($script:B)Always allow from this computer$($script:R), then OK."
            if (-not $script:UI) { return $false }
        }
        Out-Raw "`r$($script:Esc)[K  $($script:Acc)$($script:Spin[$i % $script:Spin.Count])$($script:R) $($script:D)waiting for the TV to allow this computer (${i}s)$($script:R)"
        Start-Sleep -Seconds 1
    }
    if ($script:UI) { Out-Raw "`r$($script:Esc)[K" }
    return $false
}
function Apply-Pair {
    $addr = $Pair
    $code = $Code
    if (-not $addr) {
        Note 'On the TV: Developer options, Wireless debugging, Pair with pairing code. It shows an address and a 6-digit code.'
        $addr = Ask 'Pairing address (IP:port):'
    }
    if (-not (ValidEndpoint $addr)) { Die 'that does not look like HOST:PORT' }
    if (-not $code) { $code = Ask 'Pairing code (6 digits):' }
    if ($code -notmatch '^[0-9]{6}$') { Die 'the pairing code is six digits' }
    CmdLine "adb pair $addr ******"
    $r = Invoke-Native $script:Adb @('pair', $addr, $code) 'Pairing' 30
    if ($r.Out -match 'Successfully paired') {
        Good 'Paired. Now connect with the address on the main Wireless debugging screen (a different port).'
        return $true
    }
    Bad "Pairing failed: $($r.Out.Trim())"
    Note 'The code changes each time the pairing box is opened. Keep that box open on the TV while pairing.'
    return $false
}
function Discover-Mdns {
    $r = Invoke-Native $script:Adb @('mdns', 'services') '' 10
    $found = @()
    foreach ($line in ($r.Out -split "`n")) {
        if ($line -match '_adb-tls-connect') { $parts = $line.Trim() -split '\s+'; $found += $parts[$parts.Count - 1] }
    }
    return @($found | Sort-Object -Unique)
}
# Pick-Tv: sets $script:Device and $script:Rc (0 done, 2 left without choosing).
function Pick-Tv {
    if ($Tv) {
        if (-not (Connect-Endpoint $Tv)) { Die "could not reach $Tv" }
        if (-not (Await-Authorized)) { Die 'the TV did not allow this computer. Accept the prompt on the TV and run this again.' }
        $script:Rc = 0
        return
    }
    while ($true) {
        $serials = @(); $labels = @(); $found = @(); $options = @(); $descs = @()
        $r = Invoke-Native $script:Adb @('devices', '-l')
        foreach ($line in ($r.Out -split "`n" | Select-Object -Skip 1)) {
            $line = $line.Trim()
            if (-not $line -or $line -like '*daemon*') { continue }
            $parts = $line -split '\s+'
            if ($parts.Count -lt 2) { continue }
            switch ($parts[1]) {
                'device' {
                    $model = ''
                    $m = [regex]::Match($line, 'model:(\S+)')
                    if ($m.Success) { $model = "  ($($m.Groups[1].Value))" }
                    $serials += $parts[0]; $labels += "$($parts[0])$model"
                }
                'unauthorized' { Note "$($parts[0]) is waiting for you to allow it on the TV." }
                'offline' { Note "$($parts[0]) is offline. Wake the TV or reconnect it." }
            }
        }
        $saved = SavedEndpoints
        foreach ($f in (Discover-Mdns)) { if ($serials -notcontains $f) { $found += $f } }
        if (-not $script:UI -or $Yes) {
            if ($serials.Count -eq 0) { Die 'no TV found. On the TV turn on USB debugging (Settings, System, Developer options), then run again with -Tv <TV IP>:5555' }
            if ($serials.Count -gt 1) { Die 'several TVs are connected; choose one with -Tv ADDRESS' }
            $script:Device = $serials[0]
            $script:Rc = 0
            return
        }
        foreach ($l in $labels) { $options += $l; $descs += 'Already connected through adb' }
        foreach ($f in $found) { $options += "Found on your network: $f"; $descs += 'Announced by Wireless debugging; connects without typing' }
        foreach ($s in $saved) { $options += "Saved address: $s"; $descs += 'An address you chose to remember' }
        $options += 'Type an address (IP or host[:port])'; $descs += "Find the IP in the TV's Settings, Network"
        $options += 'Pair with a code (Android 11+ Wireless debugging)'; $descs += 'For TVs that show a pairing code'
        $options += 'Look again'; $descs += 'Refresh this list'
        $script:MenuDesc = $descs
        Menu 'Which TV?' $options
        if ($script:Rc -ne 0) { $script:Rc = 2; return }
        $opt = $script:Choice
        $nDev = $serials.Count; $nFound = $found.Count; $nSaved = $saved.Count; $total = $options.Count
        if ($opt -le $nDev) { $script:Device = $serials[$opt - 1]; $script:Rc = 0; return }
        elseif ($opt -le ($nDev + $nFound)) { $ep = $found[$opt - $nDev - 1]; if ((Connect-Endpoint $ep) -and (Await-Authorized)) { $script:Rc = 0; return } }
        elseif ($opt -le ($nDev + $nFound + $nSaved)) { $ep = $saved[$opt - $nDev - $nFound - 1]; if ((Connect-Endpoint $ep) -and (Await-Authorized)) { $script:Rc = 0; return } }
        elseif ($opt -eq ($total - 2)) { $ep = Ask 'TV address:'; if ((Connect-Endpoint $ep) -and (Await-Authorized)) { $script:Rc = 0; return } }
        elseif ($opt -eq ($total - 1)) { $script:Pair = ''; $script:Code = ''; [void](Apply-Pair) }
    }
}

# ---- What is on the TV ----------------------------------------------------------------
$script:TvModel = ''; $script:TvAndroid = ''; $script:TvSdk = 0; $script:TvIsTv = $true; $script:TvHas = ''
function Inspect-Tv {
    $script:TvModel = "$(Prop 'ro.product.manufacturer') $(Prop 'ro.product.model')"
    $script:TvAndroid = Prop 'ro.build.version.release'
    $sdk = 0
    [void][int]::TryParse((Prop 'ro.build.version.sdk'), [ref]$sdk)
    $script:TvSdk = $sdk
    $script:TvIsTv = -not ((Tv @('cmd', 'package', 'has-feature', 'android.software.leanback')) -eq 'false')
    $script:TvHas = ''
    if (IsInstalled $script:Pkg) { $script:TvHas = InstalledVersion; if (-not $script:TvHas) { $script:TvHas = '?' } }
}
function Tv-Card {
    $lines = @($script:TvModel, "Android $($script:TvAndroid) (API $($script:TvSdk))", "Connected as $($script:Device)")
    if ($script:TvHas) { $lines += "shangOS $($script:TvHas) is installed" } else { $lines += 'shangOS is not installed yet' }
    Box 'Your TV' $lines
}
function Check-Tv {
    if ($script:TvSdk -gt 0 -and $script:TvSdk -lt $script:MinSdk) {
        Bad "This TV runs Android $($script:TvAndroid). shangOS needs Android 12 or newer."
        if (-not (Confirm 'Try anyway?' $false)) { return $false }
    }
    if (-not $script:TvIsTv) {
        Warn 'This device does not report itself as an Android TV.'
        if (-not (Confirm 'Install anyway?' $false)) { return $false }
    }
    return $true
}

# ---- Release ----------------------------------------------------------------------------
function Get-ReleaseInfo {
    $headers = @{ 'Accept' = 'application/vnd.github+json'; 'User-Agent' = "shangos-setup/$($script:SetupVersion)" }
    $url = "https://api.github.com/repos/$($script:Repo)/releases/latest"
    if ($Release) { $url = "https://api.github.com/repos/$($script:Repo)/releases/tags/$Release" }
    $ProgressPreference = 'SilentlyContinue'
    try { return (Invoke-RestMethod -Uri $url -Headers $headers -UseBasicParsing) }
    catch {
        $code = 0
        try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        if ($code -eq 404 -and -not $Release) { # only pre-releases exist: take the newest
            try { $all = Invoke-RestMethod -Uri "https://api.github.com/repos/$($script:Repo)/releases?per_page=1" -Headers $headers -UseBasicParsing; return @($all)[0] } catch {}
        }
        if ($code -eq 403 -or $code -eq 429) { Bad "GitHub is rate-limiting this network. Wait a few minutes, or download the APK from https://github.com/$($script:Repo)/releases/latest and use -Apk <file>." }
        elseif ($code -eq 404) { Bad "There is no published release$(if ($Release) { " $Release" }) yet." }
        else { Bad "Could not reach github.com ($($_.Exception.Message)). Check the internet connection." }
        return $null
    }
}
function Get-File([string]$label, [string]$url, [string]$dest, [long]$total) {
    CmdLine "download $url"
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(10)
    [void]$client.DefaultRequestHeaders.UserAgent.ParseAdd("shangos-setup/$($script:SetupVersion)")
    $ok = $false
    $out = $null
    try {
        $resp = $client.GetAsync($url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
        [void]$resp.EnsureSuccessStatusCode()
        if ($resp.Content.Headers.ContentLength) { $total = [long]$resp.Content.Headers.ContentLength }
        $in = $resp.Content.ReadAsStreamAsync().Result
        $out = [IO.File]::Create($dest)
        $buf = New-Object byte[] 65536
        $got = 0L
        $barw = [Math]::Max(8, [Math]::Min(40, (Cols) - 34))
        $tick = Get-Date
        $i = 0
        CursorHide
        while (($n = $in.Read($buf, 0, $buf.Length)) -gt 0) {
            $out.Write($buf, 0, $n)
            $got += $n
            if ($script:UI -and ((Get-Date) - $tick).TotalMilliseconds -ge 100) {
                $tick = Get-Date
                $sp = $script:Spin[$i % $script:Spin.Count]
                $i++
                if ($total -gt 0) {
                    $pct = [int]([Math]::Min(100, $got * 100 / $total))
                    $fill = [int]($pct * $barw / 100)
                    Out-Raw "`r$($script:Esc)[K  $($script:Acc)$sp$($script:R) $label $($script:Acc)$(Rep $script:GFull $fill)$($script:D)$(Rep $script:GEmpty ($barw - $fill))$($script:R) $pct% $($script:D)$(Fmt-Size $got)$($script:R)"
                }
                else { Out-Raw "`r$($script:Esc)[K  $($script:Acc)$sp$($script:R) $label $($script:D)$(Fmt-Size $got)$($script:R)" }
            }
        }
        $out.Close(); $out = $null
        $ok = $true
        if ($script:UI) { Out-Raw "`r$($script:Esc)[K  $($script:Ok)$($script:GOk)$($script:R) $label $($script:D)$(Fmt-Size $got)$($script:R)`n" } else { Say "  downloaded $label" }
    }
    catch {
        if ($script:UI) { Out-Raw "`r$($script:Esc)[K  $($script:Er)$($script:GEr)$($script:R) $label`n" }
        Note $_.Exception.Message
    }
    finally { if ($out) { $out.Close() }; $client.Dispose(); CursorShow }
    return $ok
}
function Fmt-Size([long]$b) { if ($b -ge 1048576) { return ('{0:N1} MB' -f ($b / 1048576)) }; return ('{0:N0} KB' -f ($b / 1024)) }
function Signer-Of([string]$apk) {
    $tool = $null
    $c = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($c) { $tool = $c.Source }
    else {
        foreach ($root in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
            if ($root -and (Test-Path (Join-Path $root 'build-tools'))) {
                $f = Get-ChildItem (Join-Path $root 'build-tools') -Recurse -Filter 'apksigner.bat' -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
                if ($f) { $tool = $f.FullName; break }
            }
        }
    }
    if (-not $tool) { return '' }
    try {
        $o = & $tool verify --print-certs $apk 2>$null
        foreach ($l in @($o)) { if ($l -match 'Signer #1 certificate SHA-256 digest: (\S+)') { return $Matches[1] } }
    }
    catch {}
    return ''
}
$script:ApkPath = ''
function Download-Release {
    $info = Get-ReleaseInfo
    if (-not $info) { return $false }
    $asset = @($info.assets | Where-Object { $_.name -match "^shangos-[^/]*-$Variant\.apk$" })[0]
    $sums = @($info.assets | Where-Object { $_.name -eq 'SHA256SUMS' })[0]
    $prefix = "https://github.com/$($script:Repo)/releases/download/"
    if (-not $asset -or -not $sums -or -not $asset.browser_download_url.StartsWith($prefix) -or -not $sums.browser_download_url.StartsWith($prefix)) {
        Bad "The release has no $Variant APK with a checksum file, or its files are not on github.com/$($script:Repo)."
        return $false
    }
    Good "Release $($info.tag_name): $($asset.name)"
    $dest = Join-Path $script:Tmp $asset.name
    if (-not (Get-File "Downloading $($asset.name)" $asset.browser_download_url $dest ([long]$asset.size))) { return $false }
    CmdLine "download $($sums.browser_download_url)"
    $wc = New-Object Net.WebClient
    $wc.Headers.Add('User-Agent', "shangos-setup/$($script:SetupVersion)")
    try { $sumText = $wc.DownloadString($sums.browser_download_url) } catch { Bad "Could not read SHA256SUMS: $($_.Exception.Message)"; return $false }
    $expected = ''
    foreach ($l in ($sumText -split "`n")) {
        if ($l -match '^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$' -and $Matches[2] -eq $asset.name) { $expected = $Matches[1].ToLower(); break }
    }
    $actual = (Get-FileHash -Algorithm SHA256 -Path $dest).Hash.ToLower()
    if (-not $expected -or $expected -ne $actual) {
        Remove-Item $dest -Force -ErrorAction SilentlyContinue
        Bad 'The download does not match its published checksum, so it was deleted.'
        return $false
    }
    Good "Checksum verified $($script:D)$actual$($script:R)"
    $cert = Signer-Of $dest
    if ($cert) { Good "Signing certificate SHA-256 $($script:D)$cert$($script:R)"; Note 'Compare with the release page and Settings, Privacy, This build (on the TV).' }
    else { Note 'apksigner is not installed here, so the signing certificate is not shown. Compare it on the TV: Settings, Privacy, This build.' }
    $script:ApkPath = $dest
    return $true
}

# ---- The actions ------------------------------------------------------------------------
function Apply-Install([string]$apkFile) {
    $name = Split-Path $apkFile -Leaf
    $flags = @('install', '-r')
    $help = Invoke-Native $script:Adb @('install', '--help')
    if ($help.Out -match '--no-incremental') { $flags = @('install', '--no-incremental', '-r') }
    $r = Adb ($flags + @($apkFile)) "Installing $name" -Mutates
    if ($r.Code -ne 0 -or $r.Out -notmatch 'Success') {
        if ($r.Out -match 'UPDATE_INCOMPATIBLE|signatures do not match') {
            Warn 'The TV has shangOS signed with another key (a different build). Android will not update it in place.'
            Note "Removing it clears shangOS's own settings (wallpaper, order). Nothing else on the TV is touched."
            if (Confirm 'Remove the old one and install this?' $false) {
                $u = Adb @('uninstall', $script:Pkg) 'Removing the old build' -Mutates
                if ($u.Code -ne 0) { ShowTail; return $false }
                $r = Adb ($flags + @($apkFile)) "Installing $name" -Mutates
                if ($r.Out -match 'Success') { return $true }
            }
        }
        elseif ($r.Out -match 'VERSION_DOWNGRADE') {
            Warn 'The TV has a newer shangOS than this one.'
            if (Confirm 'Install the older one anyway?' $false) {
                $r = Adb (@('install', '-d', '-r') + @($apkFile)) "Installing $name (downgrade)" -Mutates
                if ($r.Out -match 'Success') { return $true }
            }
        }
        ShowTail
        return $false
    }
    return $true
}
function Apply-Aot {
    $r = Adb @('shell', 'cmd', 'package', 'compile', '-m', 'speed', '-f', $script:Pkg) 'Compiling ahead of time' -Mutates
    if ($r.Code -ne 0) { Note '(not available on this TV; harmless)' }
}
function Apply-Widgets {
    $r = Adb @('shell', 'pm', 'grant', $script:Pkg, 'android.permission.READ_TV_LISTINGS') 'Up Next: Continue watching' -Mutates; if ($r.Code -ne 0) { ShowTail }
    $r = Adb @('shell', 'cmd', 'notification', 'allow_listener', "$($script:Pkg)/$($script:Pkg).MediaListener") 'Now Playing: music on the TV' -Mutates; if ($r.Code -ne 0) { ShowTail }
    $r = Adb @('shell', 'appops', 'set', $script:Pkg, 'GET_USAGE_STATS', 'allow') 'Up Next: recently used apps' -Mutates; if ($r.Code -ne 0) { ShowTail }
}
function Apply-Saver {
    $state = SaverFile
    $current = Tv @('settings', 'get', 'secure', 'screensaver_components')
    if ($current -ne $script:Ambient) { Set-Content -Path $state -Value "previous=$current" }
    $r = Adb @('shell', 'settings', 'put', 'secure', 'screensaver_components', $script:Ambient) 'Screen saver: clock over your wallpaper' -Mutates
    if ($r.Code -ne 0) { return $false }
    $r = Adb @('shell', 'settings', 'put', 'secure', 'screensaver_enabled', '1') 'Screen saver: on' -Mutates
    return ($r.Code -eq 0)
}
function Undo-Saver {
    $state = SaverFile
    $current = Tv @('settings', 'get', 'secure', 'screensaver_components')
    if ($current -ne $script:Ambient) { return }
    $previous = ''
    if (Test-Path $state) { $m = Select-String -Path $state -Pattern '^previous=(.*)$' | Select-Object -First 1; if ($m) { $previous = $m.Matches[0].Groups[1].Value } }
    if (-not $previous -or $previous -eq 'null' -or $previous -eq $script:Ambient) {
        [void](Adb @('shell', 'settings', 'delete', 'secure', 'screensaver_components') "Screen saver: the TV's own again" -Mutates)
    }
    else { [void](Adb @('shell', 'settings', 'put', 'secure', 'screensaver_components', $previous) "Screen saver: the TV's own again" -Mutates) }
    Remove-Item $state -Force -ErrorAction SilentlyContinue
}
# ---- Private DNS: optional ad and tracker blocking for the whole TV ----------------------
# Android's own Private DNS setting (Android 9+). Every app that uses the system resolver asks this server, which
# refuses known ad and tracker domains. Apps with their own or hard-coded DNS are not affected (that needs a router rule).
function ValidDnsHost([string]$h) { return (($h -match '^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$') -and ($h -like '*.*')) }
function DnsHostOf([string]$c) { if ($c -in @('adguard', 'AdGuard', 'adguard-dns')) { return 'dns.adguard-dns.com' }; return $c }
function DnsLookupWorks { return ((Tv @('ping', '-c', '1', '-W', '5', 'example.com')) -match '1 (packets )?received|bytes from') }
function Apply-Dns([string]$choice) {
    $dnsHost = DnsHostOf $choice
    if (-not (ValidDnsHost $dnsHost)) { Bad "`"$dnsHost`" is not a hostname (it looks like dns.example.com)."; return $false }
    $state = DnsFile
    $mode = Tv @('settings', 'get', 'global', 'private_dns_mode')
    $spec = Tv @('settings', 'get', 'global', 'private_dns_specifier')
    if (-not ((Test-Path $state) -and (Get-Item $state).Length -gt 0)) { Set-Content -Path $state -Value @("mode=$mode", "hostname=$spec") }
    $worked = DnsLookupWorks
    $r = Adb @('shell', 'settings', 'put', 'global', 'private_dns_specifier', $dnsHost) "Private DNS server: $dnsHost" -Mutates
    if ($r.Code -ne 0) { return $false }
    $r = Adb @('shell', 'settings', 'put', 'global', 'private_dns_mode', 'hostname') 'Private DNS: on' -Mutates
    if ($r.Code -ne 0) { return $false }
    Start-Sleep -Seconds 2
    if ($worked -and -not (DnsLookupWorks)) {
        Warn "The TV cannot look up names with $dnsHost. Putting the old setting back so the TV stays online."
        [void](Undo-Dns)
        return $false
    }
    Good "Ads and trackers on the TV now go through $dnsHost."
    Note 'Some apps may show fewer ads or load differently. Undo: setup.ps1 -Dns off'
    return $true
}
function Undo-Dns {
    $state = DnsFile
    $mode = ''; $spec = ''
    if (Test-Path $state) {
        $m = Select-String -Path $state -Pattern '^mode=(.*)$' | Select-Object -First 1; if ($m) { $mode = $m.Matches[0].Groups[1].Value }
        $m = Select-String -Path $state -Pattern '^hostname=(.*)$' | Select-Object -First 1; if ($m) { $spec = $m.Matches[0].Groups[1].Value }
    }
    if ($mode -notin @('hostname', 'off', 'opportunistic')) { $mode = 'opportunistic' }
    if ($mode -eq 'hostname' -and (ValidDnsHost $spec)) {
        $r = Adb @('shell', 'settings', 'put', 'global', 'private_dns_specifier', $spec) "Private DNS server: $spec (as before)" -Mutates
        if ($r.Code -ne 0) { return $false }
    }
    $label = $mode; if ($mode -eq 'opportunistic') { $label = 'automatic' }
    $r = Adb @('shell', 'settings', 'put', 'global', 'private_dns_mode', $mode) "Private DNS: back to $label" -Mutates
    if ($r.Code -ne 0) { return $false }
    Remove-Item $state -Force -ErrorAction SilentlyContinue
    return $true
}
function DnsStatusLine {
    $mode = Tv @('settings', 'get', 'global', 'private_dns_mode')
    $spec = Tv @('settings', 'get', 'global', 'private_dns_specifier')
    if ($mode -eq 'hostname') { return "$spec (Private DNS)" }
    if ($mode -eq 'off') { return 'off' }
    return "automatic (the TV's default)"
}
function Flow-Dns {
    $choice = $Dns
    if ($script:UI) { Say '' }
    Pick-Tv
    if ($script:Rc -ne 0) { $script:Rc = 2; return }
    Inspect-Tv
    if ($script:UI) { Tv-Card }
    if ($script:TvSdk -gt 0 -and $script:TvSdk -lt 28) { Die 'Private DNS needs Android 9 or newer.' }
    if (-not $choice) {
        $script:MenuDesc = @('Ads, trackers and known malicious domains are refused by name, for every app that uses the TV''s DNS. Free and public.', 'A hostname you control or trust: a NextDNS profile, your own AdGuard Home, Pi-hole with DNS-over-TLS.', 'Back to the TV''s own setting (automatic). Whatever you had before is restored.')
        $script:MenuStart = 1
        Menu 'Ad and tracker blocking' @('Block ads and trackers (AdGuard DNS)', 'Use my own DNS hostname', 'Turn it off (back to automatic)')
        if ($script:Rc -ne 0) { $script:Rc = 0; return }
        switch ($script:Choice) {
            1 { $choice = 'adguard' }
            2 { $choice = Ask 'Hostname (for example dns.example.com)' '' }
            3 { $choice = 'off' }
        }
    }
    if ($choice -in @('off', 'none', 'automatic')) {
        if (Undo-Dns) { Good 'The TV''s DNS is automatic again.' }
        $script:Rc = 0; return
    }
    if ($script:UI -and -not $Yes) {
        Note 'This changes one system setting for the whole TV (all apps). It can be undone with one command.'
        if (-not (Confirm 'Go ahead?' $true)) { Say 'Nothing was changed.'; $script:Rc = 0; return }
    }
    if (Apply-Dns $choice) { $script:Rc = 0 } else { $script:Rc = 1 }
}
function Apply-Home {
    $state = StateFile
    if (-not (Test-Path $state) -or (Get-Item $state).Length -eq 0) { Set-Content -Path $state -Value "original=$(HomeNow)" }
    if (-not $DisableStock) {
        foreach ($pkg in $script:Known) {
            if ((IsInstalled $pkg) -and ((Tv @('pm', 'list', 'packages', '-d', $pkg)) -match [regex]::Escape("package:$pkg"))) {
                Warn "$pkg was switched off. Some TVs cannot restart without it, so it is being switched back on."
                [void](Adb @('shell', 'pm', 'enable', '--user', '0', $pkg) "Switching $pkg back on" -Mutates)
            }
        }
    }
    $r = Adb @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $script:HomeAct) 'Making shangOS the home screen' -Mutates
    if ($r.Code -ne 0) { ShowTail; return $false }
    [void](Adb @('shell', 'cmd', 'role', 'add-role-holder', '--user', '0', 'android.app.role.HOME', $script:Pkg) 'Asking Android to make shangOS the Home role holder' -Mutates)
    $current = HomeNow
    if ($current -ne $script:HomeAct -and -not $DisableStock) {
        Warn "shangOS is chosen as the home screen, but the TV still opens $current at Home."
        Warn "The TV's own home and setup apps stay on: on some Google TVs (a Sony Bravia, for one) the TV cannot start without them."
        Write-Host '    Open shangOS from the app row, or choose "Always" if the TV asks which home to use.'
        Write-Host '    Knowing the risk, you can run again with -DisableStock. Undo: -Uninstall, or see https://shangos.madusha.dev/setup/#loop'
        return $true
    }
    if ($current -ne $script:HomeAct) {
        Warn 'Switching off a stock launcher can leave some Google TVs unable to restart (they loop at every restart).'
        if (-not $Yes -and -not (Confirm "Switch $current off anyway?" $false)) { Bad 'Nothing was changed.'; return $false }
    }
    for ($n = 0; $n -lt 3; $n++) {
        $current = HomeNow
        if ($current -eq $script:HomeAct) { break }
        $pkg = ($current -split '/')[0]
        if ($script:Disablable -contains $pkg) {
            Warn "Switching off $pkg (reversible; the setup app is never touched)."
            $r = Adb @('shell', 'pm', 'disable-user', '--user', '0', $pkg) "Switching off $pkg" -Mutates
            if ($r.Code -ne 0) { ShowTail; return $false }
            Add-Content -Path $state -Value "disabled=$pkg"
            $r = Adb @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $script:HomeAct) 'Making shangOS the home screen' -Mutates
            if ($r.Code -ne 0) { ShowTail; return $false }
        }
        else { Bad "The TV still opens $current at Home, and this script never switches that off. Nothing else was changed; run with -Uninstall to undo."; return $false }
    }
    if ((HomeNow) -ne $script:HomeAct) { Bad 'The TV did not accept shangOS as its home screen. Run with -Uninstall to undo.'; return $false }
    ShowHome
    Good 'shangOS is the home screen.'
    return $true
}
function Apply-Restore {
    $state = StateFile
    $disabled = @(); $original = ''
    if ((Test-Path $state) -and (Get-Item $state).Length -gt 0) {
        $lines = Get-Content $state
        $disabled = @($lines | Where-Object { $_ -match '^disabled=' } | ForEach-Object { $_ -replace '^disabled=', '' })
        $o = @($lines | Where-Object { $_ -match '^original=' })[0]
        if ($o) { $original = $o -replace '^original=', '' }
    }
    else {
        foreach ($pkg in $script:Known) {
            if ((IsInstalled $pkg) -and ((Tv @('pm', 'list', 'packages', '-d', $pkg)) -match [regex]::Escape("package:$pkg"))) { $disabled += $pkg }
        }
    }
    foreach ($pkg in $disabled) { [void](Adb @('shell', 'pm', 'enable', '--user', '0', $pkg) "Switching $pkg back on" -Mutates) }
    if ($original -and $original -ne $script:HomeAct -and $original -match '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$') {
        [void](Adb @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $original) "Giving Home back to $original" -Mutates)
    }
    Remove-Item $state -Force -ErrorAction SilentlyContinue
    ShowHome
    Good "Home now opens $(HomeNow)."
}

# ---- Flows ------------------------------------------------------------------------------
function Make-Plan {
    $plan = @()
    if ($Apk) { $plan += "Use $Apk" }
    else {
        $tag = '<latest>'; if ($Release) { $tag = $Release }
        $plan += "Download shangos-$tag-$Variant.apk from github.com/$($script:Repo)"
        $plan += 'Check it against the published SHA256SUMS'
    }
    $plan += 'adb install -r  (the TV keeps your shangOS settings on an update)'
    if ($DoAot) { $plan += "adb shell cmd package compile -m speed -f $($script:Pkg)" }
    if ($DoWidgets) { $plan += "adb shell pm grant $($script:Pkg) READ_TV_LISTINGS"; $plan += 'adb shell cmd notification allow_listener ...'; $plan += "adb shell appops set $($script:Pkg) GET_USAGE_STATS allow" }
    if ($DoHome) { $plan += "adb shell cmd package set-home-activity $($script:HomeAct)"; $plan += "(the TV's own home and setup apps are left on; with -DisableStock, a stock launcher that wins is switched off after asking)" }
    if ($DoSaver) { $plan += "adb shell settings put secure screensaver_components $($script:Ambient)" }
    if ($DoDns) { $plan += 'adb shell settings put global private_dns_specifier dns.adguard-dns.com'; $plan += 'adb shell settings put global private_dns_mode hostname  (blocks ads and trackers for every app; undone with -Dns off)' }
    return $plan
}
function Run-Install {
    $failed = $false
    if (-not $Apk) { if (-not (Download-Release)) { return $false }; $file = $script:ApkPath }
    else { if (-not (Test-Path $Apk)) { Die "no APK at $Apk" }; $file = (Resolve-Path $Apk).Path }
    if (-not (Apply-Install $file)) { return $false }
    if ($DoAot) { Apply-Aot }
    if ($DoWidgets) { Apply-Widgets }
    if ($DoHome) { if (-not (Apply-Home)) { $failed = $true } }
    if ($DoSaver) { if (-not (Apply-Saver)) { $failed = $true } }
    if ($DoDns) { if (-not (Apply-Dns 'adguard')) { $failed = $true } }
    return (-not $failed)
}
function Finish-Card {
    Say ''
    Box 'Done' @('shangOS is on your TV.', 'Press Home on the remote. Settings (the gear) walks you through the rest.', '', 'Undo any time, in PowerShell:', "  & ([scriptblock]::Create((New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1'))) -Uninstall", "What Undo needs is remembered in $($script:ConfigDir)")
}
# Flow-Install: $script:Rc 0 done, 1 failed, 2 left.
function Flow-Install {
    $s = 1
    while ($s -ge 1 -and $s -le 5) {
        switch ($s) {
            1 {
                if ($script:UI) { Say ''; StepBar 1 }
                Pick-Tv
                if ($script:Rc -ne 0) { $script:Rc = 2; return }
                Inspect-Tv
                if ($script:UI) { Tv-Card }
                if (-not (Check-Tv)) {
                    if ($script:UI -and -not $Yes) { continue }
                    $script:Rc = 1; return
                }
                $s = 2
            }
            2 {
                if ($Apk -or $Yes -or -not $script:UI) { $s = 3; continue }
                $script:MenuDesc = @('The normal build. Weather (off until you turn it on) and Up Next pictures use the internet.', 'No internet permission at all: Android itself blocks every connection. No weather, no remote posters.')
                if ($Variant -eq 'offline') { $script:MenuStart = 2 }
                Menu 'Which build?' @('Standard (release)', 'Offline (no internet permission)')
                if ($script:Rc -eq 1) { $s = 1; continue }
                if ($script:Rc -eq 2) { return }
                if ($script:Choice -eq 2) { $script:Variant = 'offline' } else { $script:Variant = 'release' }
                $s = 3
            }
            3 {
                if ($Yes) { $s = 4; continue }
                $script:MenuDesc = @('Up Next, Now Playing and recent apps. Android asks for these in Settings; this grants them over adb.', "Press Home and shangOS opens. If the TV's own launcher wins, it is switched off, and Undo switches it back on.", 'Compiles the app for speed once; a few seconds. Harmless if the TV cannot.', 'A clock over your wallpaper after the TV''s own screen-saver delay. Off by default.', 'Uses AdGuard DNS as the TV''s Private DNS: ad and tracker domains are refused for every app. Off by default; one command undoes it.')
                $script:Chk = @($DoWidgets, $DoHome, $DoAot, $DoSaver, $DoDns)
                Checklist 'What should I switch on?' @('Optional permissions (Up Next, Now Playing)', 'Make shangOS the home screen', 'Compile ahead of time (faster)', 'shangOS screen saver', 'Block ads and trackers on the whole TV (Private DNS)')
                if ($script:Rc -eq 1) { $s = 2; continue }
                if ($script:Rc -eq 2) { return }
                $script:DoWidgets = [bool]$script:Chk[0]; $script:DoHome = [bool]$script:Chk[1]; $script:DoAot = [bool]$script:Chk[2]; $script:DoSaver = [bool]$script:Chk[3]; $script:DoDns = [bool]$script:Chk[4]
                $s = 4
            }
            4 {
                if ($script:UI -and -not $Yes) {
                    Say ''
                    Box 'Here is exactly what will happen' (Make-Plan)
                    Say ''
                    if (-not (Confirm 'Go ahead?' $true)) { $s = 3; continue }
                }
                $s = 5
            }
            5 {
                if ($script:UI) { Say ''; StepBar 5 }
                if (Run-Install) { Finish-Card; $script:Rc = 0; return }
                if ($script:Changed) { Bad 'Setup stopped. The commands listed above did run; undo them with -Uninstall.' }
                else { Bad 'Setup stopped before anything on the TV was changed.' }
                $script:Rc = 1
                return
            }
        }
    }
    $script:Rc = 0
}
function Flow-Uninstall {
    if ($script:UI) { Say '' }
    Pick-Tv
    if ($script:Rc -ne 0) { $script:Rc = 2; return }
    Inspect-Tv
    if ($script:UI) { Tv-Card }
    if (-not (IsInstalled $script:Pkg)) { Die 'shangOS is not installed on this TV' }
    if ($script:UI -and -not $Yes) {
        Say ''
        if (-not (Confirm "Remove shangOS and give Home back to the TV's own launcher?" $true)) { Say 'Nothing was changed.'; $script:Rc = 0; return }
    }
    $st = StateFile
    if (((Test-Path $st) -and (Get-Item $st).Length -gt 0) -or ((HomeNow) -eq $script:HomeAct)) {
        Note "shangOS is the home screen: giving Home back first, so the TV always has a launcher."
        Apply-Restore
    }
    Undo-Saver
    $dnsState = DnsFile
    if ((Test-Path $dnsState) -and (Get-Item $dnsState).Length -gt 0) {
        if ($script:UI -and -not $Yes) { if (Confirm 'The TV''s Private DNS was set by this wizard. Turn it back to automatic too?' $false) { [void](Undo-Dns) } }
        else { Note 'Private DNS was left as it is. Turn it back with: setup.ps1 -Dns off' }
    }
    $r = Adb @('uninstall', $script:Pkg) 'Removing shangOS' -Mutates
    if ($r.Code -ne 0) { ShowTail; $script:Rc = 1; return }
    Say ''
    Good "Removed. The TV's own launcher opens at Home again."
    $script:Rc = 0
}
function Flow-Status {
    Pick-Tv
    if ($script:Rc -ne 0) { $script:Rc = 2; return }
    Inspect-Tv
    Tv-Card
    Say "  Home opens:        $(HomeNow)"
    Say "  Screen saver:      $(Tv @('settings', 'get', 'secure', 'screensaver_components'))"
    $off = @()
    foreach ($pkg in $script:Known) { if ((Tv @('pm', 'list', 'packages', '-d', $pkg)) -match [regex]::Escape("package:$pkg")) { $off += $pkg } }
    $offText = 'nothing'
    if ($off.Count -gt 0) { $offText = $off -join ' ' }
    Say "  Private DNS:       $(DnsStatusLine)"
    Say "  Switched off:      $offText"
    Say "  Saved state:       $(StateFile)"
    $script:Rc = 0
}
function Home-Menu {
    Banner
    while ($true) {
        $script:MenuDesc = @('Download the signed release, install it, make it your home screen.', "Give Home back to the TV's own launcher, then remove shangOS.", 'What is installed, what Home opens, what is switched off.', 'For TVs that show a pairing code (Android 11+ Wireless debugging).', 'Optional: refuse ad and tracker domains for every app on the TV (Private DNS). One command undoes it.')
        Menu 'What would you like to do?' @('Install or update shangOS', 'Remove shangOS (undo everything)', 'Check a TV', 'Pair with a TV', 'Block ads and trackers (DNS)')
        if ($script:Rc -ne 0) { return }
        switch ($script:Choice) {
            1 { Flow-Install }
            2 { Flow-Uninstall }
            3 { Flow-Status }
            4 { [void](Apply-Pair) }
            5 { Flow-Dns }
        }
        Say ''
        if (-not (Confirm 'Back to the main menu?' $true)) { return }
    }
}

# ---- Run ---------------------------------------------------------------------------------
$exitCode = 0
try {
    $script:Tmp = Join-Path ([IO.Path]::GetTempPath()) ("shangos-" + [Guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $script:Tmp -Force)
    Ensure-Adb
    $action = 'install'
    if ($Uninstall) { $action = 'uninstall' }
    elseif ($Status) { $action = 'status' }
    elseif ($Pair) { $action = 'pair' }
    elseif ($Dns) { $action = 'dns' }
    elseif ($script:UI -and -not $Yes -and -not $Tv -and -not $Apk) { $action = 'menu' }
    if ($script:UI -and $action -ne 'menu') { Banner }
    if (-not $script:UI) { Say "shangOS setup $($script:SetupVersion)" }
    switch ($action) {
        'menu' { Home-Menu }
        'install' { Flow-Install; $exitCode = $script:Rc }
        'uninstall' { Flow-Uninstall; $exitCode = $script:Rc }
        'status' { Flow-Status; $exitCode = $script:Rc }
        'dns' { Flow-Dns; $exitCode = $script:Rc }
        'pair' { if (-not (Apply-Pair)) { $exitCode = 1 } }
    }
    if ($exitCode -eq 2) { Say 'Nothing was changed.'; $exitCode = 0 }
}
catch [System.OperationCanceledException] { $exitCode = 1 }
catch { Bad "shangos setup: $($_.Exception.Message)"; $exitCode = 1 }
finally {
    CursorShow
    if ($script:Tmp -and (Test-Path $script:Tmp)) { Remove-Item $script:Tmp -Recurse -Force -ErrorAction SilentlyContinue }
}
# Returning (not exit) keeps the user's PowerShell window open when run through iex.
if ($exitCode -ne 0) { $global:LASTEXITCODE = $exitCode }
