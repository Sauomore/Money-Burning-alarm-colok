<#
.SYNOPSIS
    烧钱闹钟 一键构建 / 安装 / 运行脚本。

.DESCRIPTION
    工具链全部在 E 盘（JDK 21 + Android SDK + Gradle 缓存），不占用 C 盘空间。

.EXAMPLE
    .\build.ps1 run      # 编译 + 安装 + 启动（最常用）
    .\build.ps1 debug    # 只编译 debug APK
    .\build.ps1 log      # 看 App 日志
    .\build.ps1 mock     # 启动本地假 OpenAI 接口（验证焚烧链路，不花钱）
#>
param(
    [ValidateSet('debug', 'release', 'install', 'run', 'log', 'mock', 'clean')]
    [string]$Action = 'debug'
)

$ErrorActionPreference = 'Stop'

# ---------- 工具链位置（全部在 E 盘）----------
$env:GRADLE_USER_HOME = 'E:\AndroidDev\gradle-home'
$env:JAVA_HOME        = 'E:\AndroidDev\jdk21'
$env:ANDROID_HOME     = 'E:\AndroidDev\android-sdk'

$ProjectRoot = $PSScriptRoot
$Adb         = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$Device      = '127.0.0.1:16384'
$AppId       = 'com.dsh.burnalarm'
$Gradlew     = Join-Path $ProjectRoot 'gradlew.bat'
$DebugApk    = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'

function Invoke-Gradle([string[]]$Args) {
    & $Gradlew -p $ProjectRoot @Args --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE）" }
}

function Connect-Device {
    $state = & $Adb -s $Device get-state 2>$null
    if ($state -ne 'device') {
        & $Adb connect $Device | Out-Null
        Start-Sleep -Seconds 2
    }
    & $Adb -s $Device get-state | Out-Null
    Write-Host "设备已连接: $Device" -ForegroundColor Green
}

switch ($Action) {

    'debug' {
        Invoke-Gradle @('assembleDebug')
        Write-Host "APK: $DebugApk" -ForegroundColor Green
    }

    'release' {
        Invoke-Gradle @('assembleRelease')
    }

    'install' {
        Invoke-Gradle @('assembleDebug')
        Connect-Device
        & $Adb -s $Device install -r $DebugApk
    }

    'run' {
        Invoke-Gradle @('assembleDebug')
        Connect-Device
        & $Adb -s $Device install -r $DebugApk
        & $Adb -s $Device shell am start -n "$AppId/.MainActivity" | Out-Null
        Write-Host '已启动。' -ForegroundColor Green
    }

    'log' {
        Connect-Device
        Write-Host '输出日志中（Ctrl+C 退出）...' -ForegroundColor Cyan
        & $Adb -s $Device logcat -v color `
            -s AndroidRuntime:E `
            -s "$AppId:V" `
            -s ActivityTaskManager:E
    }

    'mock' {
        Write-Host '启动本地假 OpenAI 接口（端口 8765）' -ForegroundColor Cyan
        Write-Host '在 App 设置里填: Base URL = http://10.0.2.2:8765 ，Key 随便填' -ForegroundColor Yellow
        python (Join-Path $ProjectRoot 'tools\mock_openai.py') 8765
    }

    'clean' {
        Invoke-Gradle @('clean')
    }
}
