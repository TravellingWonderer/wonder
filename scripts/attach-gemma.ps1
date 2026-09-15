# Copies a .litertlm from Edge Gallery into Wonder and selects Gemma on-device.
# Usage: .\scripts\attach-gemma.ps1 [-FromDownloads "model.litertlm"]

param(
    [string]$FromDownloads = ""
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

function Find-GalleryModel {
    $found = & $adb shell "find /storage/emulated/0/Android/data/com.google.ai.edge.gallery -name '*.litertlm' 2>/dev/null"
    return ($found -split "`n" | Where-Object { $_.Trim() -and $_ -notmatch "adb" } | Select-Object -First 1).Trim()
}

function Copy-ModelToWonder {
    param([string]$SourcePath)
    Write-Host "Staging model via /data/local/tmp (2+ GB, please wait)..."
    & $adb shell "cp `"$SourcePath`" /data/local/tmp/gemma.litertlm"
    & $adb shell "cat /data/local/tmp/gemma.litertlm | run-as com.wonder.provider sh -c 'mkdir -p files/models && cat > files/models/gemma.litertlm'"
    $size = & $adb shell "run-as com.wonder.provider ls -lh files/models/gemma.litertlm"
    Write-Host $size
    & $adb shell "rm -f /data/local/tmp/gemma.litertlm"
}

function Attach-Internal {
    & $adb shell "am broadcast -n com.wonder.provider/.debug.AttachGemmaReceiver -a com.wonder.provider.ATTACH_GEMMA --es file `"@internal`""
}

if ($FromDownloads) {
    Write-Host "Attaching from Downloads: $FromDownloads"
    Copy-ModelToWonder "/sdcard/Download/$FromDownloads"
    Attach-Internal
    exit $LASTEXITCODE
}

Write-Host "Looking for .litertlm in Edge Gallery storage..."
$galleryPath = Find-GalleryModel
if (-not $galleryPath) {
    $downloads = & $adb shell "ls /sdcard/Download/*.litertlm 2>/dev/null"
    if ($downloads -and $downloads -notmatch "No such file") {
        $name = (Split-Path ($downloads.Trim() -split "`n" | Select-Object -First 1) -Leaf)
        Write-Host "Found in Downloads: $name"
        Copy-ModelToWonder "/sdcard/Download/$name"
        Attach-Internal
        exit 0
    }
    Write-Error "No .litertlm found. Finish downloading in Edge Gallery first."
    exit 1
}

Write-Host "Found Gallery model: $galleryPath"
Copy-ModelToWonder $galleryPath
Attach-Internal
Write-Host "Done. Open Wonder → Conversation — header should show Gemma on-device."
