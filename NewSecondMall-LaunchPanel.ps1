$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

$Script:Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Script:Front = Join-Path $Script:Root 'shopping_front'
$Script:Back = Join-Path $Script:Root 'shopping_back\shopping_back'
$Script:Mp = Join-Path $Script:Front 'unpackage\dist\dev\mp-weixin'
$Script:DbSql = Join-Path $Script:Back 'doc\db.sql'
$Script:DeployDir = Join-Path $Script:Root 'deploy'
$Script:DeployEnv = Join-Path $Script:DeployDir '.env'
$Script:DeployEnvExample = Join-Path $Script:DeployDir '.env.example'
$Script:ComposeFile = Join-Path $Script:DeployDir 'docker-compose.yml'
$Script:DbConfig = Join-Path $Script:Root 'NewSecondMall-db.local.json'
$Script:DbHost = '127.0.0.1'
$Script:DbPort = '3306'
$Script:DbName = 'shop_db'
$Script:DbUsername = 'shop_user'
$Script:DbPassword = 'shop_pass_123'
$Script:MysqlExe = 'mysql'
$Script:HBuilderX = 'NOT FOUND'
$Script:HBuilderXCli = 'NOT FOUND'
$Script:WeChatDevTools = 'NOT FOUND'
$Script:WeChatCli = 'NOT FOUND'

function ConvertTo-QuotedPowerShellString([string]$Value) {
    return "'" + ($Value -replace "'", "''") + "'"
}

function Start-PowerShellWindow([string]$Title, [string]$Command) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
    Start-Process -FilePath 'powershell' -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encoded) -WindowStyle Normal | Out-Null
}

function Read-Default([string]$Prompt, [string]$DefaultValue) {
    $value = Read-Host "$Prompt [$DefaultValue]"
    if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultValue }
    return $value
}

function Load-DbConfig {
    if (-not (Test-Path -LiteralPath $Script:DbConfig)) { return }
    try {
        $config = Get-Content -LiteralPath $Script:DbConfig -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($config.host) { $Script:DbHost = [string]$config.host }
        if ($config.port) { $Script:DbPort = [string]$config.port }
        if ($config.name) { $Script:DbName = [string]$config.name }
        if ($config.username) { $Script:DbUsername = [string]$config.username }
        if ($null -ne $config.password) { $Script:DbPassword = [string]$config.password }
    } catch {
        Write-Host "本地数据库配置读取失败，将使用默认配置。" -ForegroundColor Yellow
    }
}

function Save-DbConfig {
    $config = [ordered]@{
        host = $Script:DbHost
        port = $Script:DbPort
        name = $Script:DbName
        username = $Script:DbUsername
        password = $Script:DbPassword
    }
    $config | ConvertTo-Json | Set-Content -LiteralPath $Script:DbConfig -Encoding UTF8
}

function Find-HBuilderX {
    $Script:HBuilderX = $null
    $Script:HBuilderXCli = $null
    $candidates = @()
    if ($env:HBUILDERX_EXE) { $candidates += $env:HBUILDERX_EXE }
    $candidates += @(
        "$env:ProgramFiles\HBuilderX\HBuilderX.exe",
        "${env:ProgramFiles(x86)}\HBuilderX\HBuilderX.exe",
        'D:\HBuilderX\HBuilderX.exe'
    )
    $candidates += Get-ChildItem -Path 'D:\HBuilderX*', 'C:\HBuilderX*', "$env:USERPROFILE\Desktop\HBuilderX*" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'HBuilderX\HBuilderX.exe' }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            $Script:HBuilderX = $candidate
            break
        }
    }
    if ($env:HBUILDERX_CLI_EXE -and (Test-Path -LiteralPath $env:HBUILDERX_CLI_EXE)) {
        $Script:HBuilderXCli = $env:HBUILDERX_CLI_EXE
    } elseif ($Script:HBuilderX) {
        $cli = Join-Path (Split-Path -Parent $Script:HBuilderX) 'cli.exe'
        if (Test-Path -LiteralPath $cli) { $Script:HBuilderXCli = $cli }
    }
    if (-not $Script:HBuilderX) { $Script:HBuilderX = 'NOT FOUND' }
    if (-not $Script:HBuilderXCli) { $Script:HBuilderXCli = 'NOT FOUND' }
}

function Find-WeChatDevTools {
    $Script:WeChatDevTools = $null
    $Script:WeChatCli = $null
    $candidates = @()
    if ($env:WECHAT_DEVTOOLS_EXE) { $candidates += $env:WECHAT_DEVTOOLS_EXE }
    $candidates += @(
        "$env:ProgramFiles\Tencent\微信web开发者工具\微信开发者工具.exe",
        "${env:ProgramFiles(x86)}\Tencent\微信web开发者工具\微信开发者工具.exe",
        "$env:LocalAppData\微信开发者工具\微信开发者工具.exe"
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            $Script:WeChatDevTools = $candidate
            break
        }
    }
    if ($env:WECHAT_DEVTOOLS_CLI -and (Test-Path -LiteralPath $env:WECHAT_DEVTOOLS_CLI)) {
        $Script:WeChatCli = $env:WECHAT_DEVTOOLS_CLI
    } elseif ($Script:WeChatDevTools) {
        $cli = Join-Path (Split-Path -Parent $Script:WeChatDevTools) 'cli.bat'
        if (Test-Path -LiteralPath $cli) { $Script:WeChatCli = $cli }
    }
    if (-not $Script:WeChatDevTools) { $Script:WeChatDevTools = 'NOT FOUND' }
    if (-not $Script:WeChatCli) { $Script:WeChatCli = 'NOT FOUND' }
}

function Find-MySql {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) {
        $Script:MysqlExe = $cmd.Source
        return
    }
    $candidates = @(
        "$env:ProgramFiles\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "${env:ProgramFiles(x86)}\MySQL\MySQL Server 8.0\bin\mysql.exe",
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            $Script:MysqlExe = $candidate
            return
        }
    }
    $Script:MysqlExe = 'mysql'
}

function Rescan-Tools {
    Find-HBuilderX
    Find-WeChatDevTools
    Find-MySql
}

function Show-Menu {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '              NewSecondMall 一键启动面板'
    Write-Host '========================================================'
    Write-Host ''
    Write-Host "项目根目录 : $Script:Root"
    Write-Host "HBuilderX  : $Script:HBuilderX"
    Write-Host "HBuilderX CLI : $Script:HBuilderXCli"
    Write-Host "微信工具   : $Script:WeChatDevTools"
    Write-Host "MySQL CLI  : $Script:MysqlExe"
    Write-Host "数据库     : $Script:DbUsername@$Script:DbHost`:$Script:DbPort/$Script:DbName"
    Write-Host ''
    Write-Host '  【本机开发】'
    Write-Host '  1. 启动后端 Spring Boot'
    Write-Host '  2. 用 HBuilderX 打开前端工程'
    Write-Host '  3. 启动或打开网页端 H5（开发预览，localhost:5173）'
    Write-Host '  4. 构建并打开微信小程序'
    Write-Host '  5. 打开小程序编译目录'
    Write-Host '  6. 打开项目根目录'
    Write-Host '  7. 重新扫描工具路径'
    Write-Host '  8. 初始化或配置 MySQL 数据库'
    Write-Host ''
    Write-Host '  【Docker 部署】'
    Write-Host '  9. 构建并启动 Docker'
    Write-Host ' 10. 停止容器（本项目全部容器）'
    Write-Host ' 11. 查看容器状态'
    Write-Host ' 12. 删除容器数据库'
    Write-Host ' 13. 重新启动容器'
    Write-Host '  0. 退出'
    Write-Host ''
}

function Start-Backend {
    if (-not (Test-Path -LiteralPath (Join-Path $Script:Back 'mvnw.cmd'))) {
        Write-Host "后端目录不正确：$Script:Back" -ForegroundColor Red
        Pause
        return
    }
    $dbUrl = 'jdbc:mysql://{0}:{1}/{2}?serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false' -f $Script:DbHost, $Script:DbPort, $Script:DbName
    $command = @"
`$env:DB_URL = $(ConvertTo-QuotedPowerShellString $dbUrl)
`$env:DB_USERNAME = $(ConvertTo-QuotedPowerShellString $Script:DbUsername)
`$env:DB_PASSWORD = $(ConvertTo-QuotedPowerShellString $Script:DbPassword)
Set-Location -LiteralPath $(ConvertTo-QuotedPowerShellString $Script:Back)
.\mvnw.cmd spring-boot:run
"@
    Start-PowerShellWindow 'NewSecondMall Backend' $command
    Write-Host '已打开后端启动窗口。看到 Tomcat started on port 8080 后，再运行接口测试。' -ForegroundColor Green
    Pause
}

function Open-HBuilderX {
    if (Test-Path -LiteralPath $Script:HBuilderX) {
        Start-Process -FilePath $Script:HBuilderX -ArgumentList @($Script:Front) | Out-Null
    } else {
        Write-Host '没有自动找到 HBuilderX。可以设置环境变量 HBUILDERX_EXE 为 HBuilderX.exe 完整路径。' -ForegroundColor Yellow
        Write-Host "前端工程目录：$($Script:Front)"
        Pause
    }
}

function Start-H5 {
    if (-not (Test-Path -LiteralPath $Script:HBuilderXCli)) {
        Write-Host '没有自动找到 HBuilderX CLI。可以先用第 2 项打开 HBuilderX 后手动运行到浏览器。' -ForegroundColor Yellow
        Pause
        return
    }
    $portOpen = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
    if ($portOpen) {
        Start-Process 'http://localhost:5173' | Out-Null
        return
    }
    $command = '& {0} launch web --project {1} --browser Edge' -f (ConvertTo-QuotedPowerShellString $Script:HBuilderXCli), (ConvertTo-QuotedPowerShellString $Script:Front)
    Start-PowerShellWindow 'NewSecondMall H5' $command
}

function Start-WeChatMiniProgram {
    if (-not (Test-Path -LiteralPath $Script:HBuilderXCli)) {
        Write-Host '没有自动找到 HBuilderX CLI，无法自动构建小程序。可以先用第 2 项打开 HBuilderX 后手动运行到微信开发者工具。' -ForegroundColor Yellow
        Pause
        return
    }
    $command = '& {0} launch mp-weixin --project {1} --runtime-log true' -f (ConvertTo-QuotedPowerShellString $Script:HBuilderXCli), (ConvertTo-QuotedPowerShellString $Script:Front)
    Start-PowerShellWindow 'NewSecondMall WeChat' $command
}

function Open-MpFolder {
    if (Test-Path -LiteralPath $Script:Mp) {
        Start-Process -FilePath $Script:Mp | Out-Null
    } else {
        Write-Host "小程序编译目录还不存在：$Script:Mp" -ForegroundColor Yellow
        Write-Host '请先运行第 4 项，或在 HBuilderX 中手动运行到微信小程序。'
        Pause
    }
}

function Get-EnvFileValue([string]$Path, [string]$Key) {
    if (-not (Test-Path -LiteralPath $Path)) { return '' }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trim = $line.Trim()
        if ($trim -eq '' -or $trim.StartsWith('#')) { continue }
        $eq = $trim.IndexOf('=')
        if ($eq -lt 1) { continue }
        $name = $trim.Substring(0, $eq)
        if ($name -eq $Key) { return $trim.Substring($eq + 1) }
    }
    return ''
}

function Set-EnvFileValue([string]$Path, [string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path -LiteralPath $Path) {
        $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    }
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match ("^\s*" + [regex]::Escape($Key) + "=")) {
            $found = $true
            "$Key=$Value"
        } else {
            $line
        }
    }
    if (-not $found) {
        $updated = @($updated) + @("$Key=$Value")
    }
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines($Path, [string[]]@($updated), $utf8)
}

function Test-DockerCli {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $cmd) {
        Write-Host '没有找到 docker 命令。请先安装 Docker Desktop，并确保 docker 已加入 PATH。' -ForegroundColor Red
        return $false
    }
    docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host '已找到 docker，但不支持 docker compose。请升级 Docker Desktop。' -ForegroundColor Red
        return $false
    }
    return $true
}

function Test-IsPlaceholder([string]$Value) {
    return [string]::IsNullOrWhiteSpace($Value) -or ($Value -match 'replace-with')
}

function Read-HiddenText([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Initialize-DeployEnv {
    if (-not (Test-Path -LiteralPath $Script:ComposeFile)) {
        Write-Host "找不到 Compose 文件：$Script:ComposeFile" -ForegroundColor Red
        return $false
    }
    if (-not (Test-Path -LiteralPath $Script:DeployEnv)) {
        if (Test-Path -LiteralPath $Script:DeployEnvExample) {
            Copy-Item -LiteralPath $Script:DeployEnvExample -Destination $Script:DeployEnv -Force
            Write-Host "已从 .env.example 复制 deploy/.env" -ForegroundColor Cyan
        } else {
            Set-Content -LiteralPath $Script:DeployEnv -Value "HTTP_PORT=80`n" -Encoding UTF8
            Write-Host "已创建空的 deploy/.env" -ForegroundColor Cyan
        }
    }

    $userName = Get-EnvFileValue $Script:DeployEnv 'DB_USERNAME'
    $dbPass = Get-EnvFileValue $Script:DeployEnv 'DB_PASSWORD'
    $rootPass = Get-EnvFileValue $Script:DeployEnv 'MYSQL_ROOT_PASSWORD'
    $origin = Get-EnvFileValue $Script:DeployEnv 'PUBLIC_ORIGIN'
    $needCreds = (Test-IsPlaceholder $dbPass) -or (Test-IsPlaceholder $rootPass) -or (Test-IsPlaceholder $userName)

    if ($needCreds) {
        Write-Host ''
        Write-Host 'Docker 容器内会新建一套 MySQL，请为它设置用户名和密码。' -ForegroundColor Cyan
        Write-Host '这不是本机 Windows / Navicat 里的 MySQL root 密码，随便定一组即可。'
        Write-Host ''
        if (Test-IsPlaceholder $userName) { $userName = 'shop_user' }
        $userName = Read-Default '数据库用户名' $userName
        if ([string]::IsNullOrWhiteSpace($userName)) { $userName = 'shop_user' }

        do {
            $dbPass = Read-HiddenText '应用密码 DB_PASSWORD（输入时不显示）'
            if ([string]::IsNullOrWhiteSpace($dbPass)) {
                Write-Host '密码不能为空，请再输入一次。' -ForegroundColor Yellow
            }
        } while ([string]::IsNullOrWhiteSpace($dbPass))

        $rootPass = Read-HiddenText '容器 MySQL root 密码（回车则与上面相同）'
        if ([string]::IsNullOrWhiteSpace($rootPass)) { $rootPass = $dbPass }

        Set-EnvFileValue $Script:DeployEnv 'DB_USERNAME' $userName
        Set-EnvFileValue $Script:DeployEnv 'DB_PASSWORD' $dbPass
        Set-EnvFileValue $Script:DeployEnv 'MYSQL_ROOT_PASSWORD' $rootPass
        Write-Host '已写入 deploy/.env（该文件不会提交到 Git）。' -ForegroundColor Green
    }

    if (Test-IsPlaceholder $origin) {
        $httpPort = Get-EnvFileValue $Script:DeployEnv 'HTTP_PORT'
        if ([string]::IsNullOrWhiteSpace($httpPort)) { $httpPort = '80' }
        $defaultOrigin = 'http://localhost'
        if ($httpPort -ne '80') { $defaultOrigin = "http://localhost:$httpPort" }
        $origin = Read-Default '浏览器访问地址 PUBLIC_ORIGIN' $defaultOrigin
        if (Test-IsPlaceholder $origin) { $origin = $defaultOrigin }
        Set-EnvFileValue $Script:DeployEnv 'PUBLIC_ORIGIN' $origin
    }

    $httpPortNow = Get-EnvFileValue $Script:DeployEnv 'HTTP_PORT'
    if ([string]::IsNullOrWhiteSpace($httpPortNow)) {
        Set-EnvFileValue $Script:DeployEnv 'HTTP_PORT' '80'
    }
    return $true
}

function Get-DockerPublicUrl {
    $origin = (Get-EnvFileValue $Script:DeployEnv 'PUBLIC_ORIGIN').Trim().TrimEnd('/')
    $port = Get-EnvFileValue $Script:DeployEnv 'HTTP_PORT'
    if (-not $port) { $port = '80' }
    if ($origin) { return $origin }
    if ($port -eq '80') { return 'http://localhost' }
    return "http://localhost:$port"
}

function Invoke-ComposeInDeployDir {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ComposeArgs)
    if (-not (Test-Path -LiteralPath $Script:DeployDir)) {
        Write-Host "找不到 deploy 目录：$Script:DeployDir" -ForegroundColor Red
        return 1
    }
    Push-Location -LiteralPath $Script:DeployDir
    try {
        if (Test-Path -LiteralPath $Script:DeployEnv) {
            & docker compose --env-file .env -f docker-compose.yml @ComposeArgs
        } else {
            & docker compose -f docker-compose.yml @ComposeArgs
        }
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Write-DockerStatus {
    Write-Host ''
    Write-Host '—— docker ps -a（本项目容器） ——' -ForegroundColor Cyan
    docker ps -a --filter 'name=deploy-' --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}\t{{.Ports}}'
    $url = Get-DockerPublicUrl
    Write-Host ''
    Write-Host "浏览器访问：$url"
}

function Write-BackendLogHint {
    $logText = ''
    try {
        $logText = docker logs deploy-backend-1 --tail 80 2>&1 | Out-String
    } catch {
        $logText = ''
    }
    if ($logText -match 'Access denied') {
        Write-Host ''
        Write-Host '诊断：MySQL 拒绝 shop_user 登录（Access denied）。' -ForegroundColor Yellow
        Write-Host '容器数据卷是第一次启动时按当时的密码初始化的；后来改 .env 不会改已有库里的密码。'
        Write-Host '课设处理：选第 12 项删库，再选第 13 项用当前 .env 重新建库启动。'
        Write-Host '第 10 项只停止容器、不删库，解决不了这个问题。'
        return
    }
    if ($logText -match 'Communications link failure|Could not create connection') {
        Write-Host ''
        Write-Host '诊断：backend 连不上 mysql。请看 mysql 是否 Up (healthy)，再选第 11 项刷新。' -ForegroundColor Yellow
        return
    }
    if ($logText -match 'APPLICATION FAILED TO START|Error') {
        Write-Host ''
        Write-Host 'backend 启动失败，上面日志里有具体原因。' -ForegroundColor Yellow
    }
}

function Show-DockerStatus {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '               Docker 容器状态'
    Write-Host '========================================================'
    Write-Host ''
    if (-not (Test-DockerCli)) { Pause; return }
    if (-not (Test-Path -LiteralPath $Script:ComposeFile)) {
        Write-Host "找不到 Compose 文件：$Script:ComposeFile" -ForegroundColor Red
        Pause
        return
    }
    Write-DockerStatus
    Pause
}

function Start-DockerStack {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '           构建并启动 Docker（部署栈）'
    Write-Host '========================================================'
    Write-Host ''
    Write-Host '第 3 项是开发预览（launch web → localhost:5173）。'
    Write-Host '本项由 Docker 在隔离的构建阶段编译后端 JAR 和前端 H5，本机不需要 Maven 或 HBuilderX。'
    Write-Host ''

    if (-not (Test-DockerCli)) { Pause; return }
    if (-not (Initialize-DeployEnv)) { Pause; return }

    Write-Host ''
    Write-Host '—— docker compose up --build ——' -ForegroundColor Cyan
    Push-Location -LiteralPath $Script:DeployDir
    try {
        docker compose --env-file .env -f docker-compose.yml up -d --build
        if ($LASTEXITCODE -ne 0) {
            Write-Host ''
            Write-Host 'docker compose 未能把三个容器都拉到健康状态。' -ForegroundColor Red
            Write-DockerStatus
            Write-Host ''
            Write-Host '—— backend 日志（末 40 行） ——' -ForegroundColor Cyan
            docker logs deploy-backend-1 --tail 40 2>&1
            Write-BackendLogHint
            Pause
            return
        }
    } finally {
        Pop-Location
    }

    $url = Get-DockerPublicUrl
    Write-Host ''
    Write-Host 'Docker 栈已启动。请稍等 mysql / backend 变为 healthy。' -ForegroundColor Green
    Write-DockerStatus
    Write-Host ''
    Write-Host '返回菜单后可选第 11 项查看容器是否 healthy。' -ForegroundColor Yellow
    try { Start-Process $url | Out-Null } catch {}
    Pause
}

function Test-ComposeReady {
    if (-not (Test-DockerCli)) { return $false }
    if (-not (Test-Path -LiteralPath $Script:ComposeFile)) {
        Write-Host "找不到 Compose 文件：$Script:ComposeFile" -ForegroundColor Red
        return $false
    }
    return $true
}

function Confirm-OrCancel([string]$Prompt) {
    Write-Host ''
    Write-Host '  YES   确认执行'
    Write-Host '  QUIT  返回菜单'
    while ($true) {
        $choice = Read-Host $Prompt
        if ($choice -ieq 'YES') { return $true }
        if ($choice -ieq 'QUIT') {
            Write-Host '已取消，返回菜单。'
            return $false
        }
        Write-Host '请输入 YES 或 QUIT。' -ForegroundColor Yellow
    }
}

function Stop-DockerStack {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '                    停止容器'
    Write-Host '========================================================'
    Write-Host ''
    if (-not (Test-ComposeReady)) { Pause; return }
    Write-Host '将停止本项目的全部容器：mysql、backend、frontend。' -ForegroundColor Yellow
    Write-Host '不会停止你电脑上其他 Docker 项目。数据库文件还在，可用第 13 项再启动。'
    if (-not (Confirm-OrCancel '确认停止全部容器')) { Pause; return }
    $code = Invoke-ComposeInDeployDir 'stop'
    if ($code -ne 0) {
        Write-Host '停止失败，请查看上面的 Docker 输出。' -ForegroundColor Red
        Pause
        return
    }
    Write-Host '已停止本项目全部容器。选第 13 项可再启动；选第 12 项才会删掉容器里的库。' -ForegroundColor Green
    Write-DockerStatus
    Pause
}

function Reset-DockerVolumes {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '                 删除容器数据库'
    Write-Host '========================================================'
    Write-Host ''
    if (-not (Test-ComposeReady)) { Pause; return }
    Write-Host '只删 Docker 里的 MySQL / 上传文件数据，本机 Windows MySQL 不动。' -ForegroundColor Yellow
    Write-Host '删卷前必须先拆掉占用它的容器，因此会执行 down -v；删完后容器也不在了。'
    Write-Host '不会自动再启动。要用空库跑起来，删完后选第 13 项。'
    Write-Host ''
    Write-Host '此操作不可恢复容器内数据。误点请输入 QUIT。' -ForegroundColor Yellow
    if (-not (Confirm-OrCancel '确认删除容器数据库')) { Pause; return }
    $code = Invoke-ComposeInDeployDir 'down' '-v'
    if ($code -ne 0) {
        Write-Host '删除失败，请查看上面的 Docker 输出。' -ForegroundColor Red
        Pause
        return
    }
    Write-Host '库已删除。请选第 13 项重新启动（按当前 .env 建空库），或第 9 项完整构建。' -ForegroundColor Green
    Pause
}

function Restart-DockerStack {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '                 重新启动容器'
    Write-Host '========================================================'
    Write-Host ''
    if (-not (Test-ComposeReady)) { Pause; return }
    if (-not (Test-Path -LiteralPath $Script:DeployEnv)) {
        Write-Host '还没有 deploy/.env。请先选第 9 项完成首次构建。' -ForegroundColor Red
        Pause
        return
    }
    Write-Host '不重新打包 JAR、不发行 H5，只把已有镜像的容器拉起来。'
    Write-Host '—— docker compose start ——' -ForegroundColor Cyan
    $code = Invoke-ComposeInDeployDir 'start'
    if ($code -ne 0) {
        Write-Host 'start 未能拉起（可能上次已拆掉容器），改用 up -d ……' -ForegroundColor Yellow
        $code = Invoke-ComposeInDeployDir 'up' '-d'
    }
    if ($code -ne 0) {
        Write-Host ''
        Write-Host '启动失败。若从未构建过请用第 9 项；若密码对不上请先第 12 项删库再第 13 项。' -ForegroundColor Red
        Write-DockerStatus
        Write-Host ''
        Write-Host '—— backend 日志（末 40 行） ——' -ForegroundColor Cyan
        docker logs deploy-backend-1 --tail 40 2>&1
        Write-BackendLogHint
        Pause
        return
    }
    Write-Host '已发出启动命令。' -ForegroundColor Green
    Write-DockerStatus
    Pause
}

function Setup-Database {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '                 MySQL 数据库配置向导'
    Write-Host '========================================================'
    Write-Host ''
    Write-Host "MySQL CLI : $Script:MysqlExe"
    Write-Host "SQL 脚本  : $Script:DbSql"
    Write-Host ''
    if (-not (Test-Path -LiteralPath $Script:DbSql)) {
        Write-Host "找不到 SQL 脚本：$Script:DbSql" -ForegroundColor Red
        Pause
        return
    }
    Write-Host '提示：数据库地址通常填 127.0.0.1 或 localhost，不是 mysql.exe 的安装路径。' -ForegroundColor Cyan
    Write-Host '上方 MySQL CLI 已经自动找到 mysql.exe，不需要在这里重复填写。'
    Write-Host ''
    $dbHostInput = Read-Default '数据库地址' $Script:DbHost
    if ($dbHostInput -match '(?i)mysql\.exe|\.exe|\\') {
        Write-Host '检测到你输入的是程序路径，不是数据库地址；已自动改回 127.0.0.1。' -ForegroundColor Yellow
        $Script:DbHost = '127.0.0.1'
    } else {
        $Script:DbHost = $dbHostInput
    }
    $Script:DbPort = Read-Default '数据库端口' $Script:DbPort
    $Script:DbName = Read-Default '数据库名' $Script:DbName
    $Script:DbUsername = Read-Default '数据库用户名' $Script:DbUsername
    $securePassword = Read-Host '数据库密码（输入时不显示，空密码直接回车）' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $Script:DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }

    $mysqlArgs = @("-h$Script:DbHost", "-P$Script:DbPort", "-u$Script:DbUsername")
    if ($Script:DbPassword) { $mysqlArgs += "-p$Script:DbPassword" }

    Write-Host ''
    Write-Host '正在测试 MySQL 连接...'
    & $Script:MysqlExe @mysqlArgs -e 'SELECT VERSION();' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'MySQL 连接失败。请确认 MySQL 服务已启动，账号密码正确。' -ForegroundColor Red
        Pause
        return
    }

    Write-Host '连接成功，正在导入初始化 SQL...'
    $tempSql = Join-Path $env:TEMP 'NewSecondMall-db-init.sql'
    Copy-Item -LiteralPath $Script:DbSql -Destination $tempSql -Force
    $sourcePath = ($tempSql -replace '\\', '/')
    & $Script:MysqlExe @mysqlArgs --default-character-set=utf8mb4 -e "source $sourcePath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'SQL 导入失败。请检查上面的 MySQL 报错。' -ForegroundColor Red
        Pause
        return
    }

    Save-DbConfig
    Write-Host ''
    Write-Host '数据库初始化完成，配置已保存到本地配置文件。' -ForegroundColor Green
    Write-Host "DB_URL=jdbc:mysql://$Script:DbHost`:$Script:DbPort/$Script:DbName"
    Write-Host "DB_USERNAME=$Script:DbUsername"
    Pause
}

function Invoke-ApiRequest([string]$Name, [string]$Method, [string]$Url, $Body = $null, [hashtable]$Headers = @{}) {
    $result = [ordered]@{ Name = $Name; Ok = $false; Status = 0; Body = $null; Error = $null }
    try {
        $params = @{ Uri = $Url; Method = $Method; Headers = $Headers; TimeoutSec = 10 }
        if ($Body -ne $null) {
            $params.ContentType = 'application/json; charset=utf-8'
            $params.Body = ($Body | ConvertTo-Json -Depth 8)
        }
        $response = Invoke-WebRequest @params -UseBasicParsing
        $result.Status = [int]$response.StatusCode
        if ($response.Content) {
            try { $result.Body = $response.Content | ConvertFrom-Json } catch { $result.Body = $response.Content }
        }
        $result.Ok = $true
    } catch {
        $result.Error = $_.Exception.Message
        if ($_.Exception.Response) {
            $result.Status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object IO.StreamReader($stream)
                $text = $reader.ReadToEnd()
                if ($text) {
                    try { $result.Body = $text | ConvertFrom-Json } catch { $result.Body = $text }
                }
            } catch {}
        }
    }
    return [pscustomobject]$result
}

function Write-TestResult([string]$Code, [bool]$Pass, [string]$Message) {
    if ($Pass) {
        Write-Host "[通过] $Code $Message" -ForegroundColor Green
    } else {
        Write-Host "[失败] $Code $Message" -ForegroundColor Red
    }
}

function Test-BackendApis {
    Clear-Host
    Write-Host '========================================================'
    Write-Host '                 后端接口基础测试'
    Write-Host '========================================================'
    Write-Host ''
    $baseUrl = Read-Default '后端地址' 'http://127.0.0.1:8080'
    $baseUrl = $baseUrl.TrimEnd('/')
    Write-Host ''
    Write-Host '正在执行测试项目清单中的 API-01 到 API-05...'
    Write-Host ''

    $products = Invoke-ApiRequest '商品列表' 'GET' "$baseUrl/api/products"
    $api01 = $products.Ok
    Write-TestResult 'API-01' $api01 '后端 8080 可访问'
    if (-not $api01) {
        Write-Host "连接失败：$($products.Error)" -ForegroundColor Yellow
        Write-Host '请先执行：8 初始化数据库 -> 1 启动后端，看到 Tomcat started on port 8080 后再测。'
        Pause
        return
    }

    $api02 = ($products.Body -and $null -ne $products.Body.code -and $null -ne $products.Body.message -and $null -ne $products.Body.data)
    Write-TestResult 'API-02' $api02 '业务接口包含 code、message、data 统一结构'

    $noToken = Invoke-ApiRequest '无 token 买家中心' 'GET' "$baseUrl/api/center/buyer"
    $api04 = (-not $noToken.Ok) -or ($noToken.Status -in 401, 403) -or ($noToken.Body -and $null -ne $noToken.Body.code -and [int]$noToken.Body.code -ne 0)
    Write-TestResult 'API-04' $api04 '未携带 token 访问受保护接口会被拦截'

    $login = Invoke-ApiRequest '登录 demo' 'POST' "$baseUrl/api/auth/login" @{ username = 'demo'; password = 'demo123' }
    $token = $null
    if ($login.Body -and $login.Body.data -and $login.Body.data.token) { $token = [string]$login.Body.data.token }
    $loginOk = $login.Ok -and $token
    Write-TestResult 'AUTH' $loginOk 'demo/demo123 登录成功并返回 token'

    $api05 = $false
    if ($token) {
        $headers = @{ Authorization = "Bearer $token" }
        $buyer = Invoke-ApiRequest '带 token 买家中心' 'GET' "$baseUrl/api/center/buyer" $null $headers
        $api05 = $buyer.Ok -and $buyer.Body -and $null -ne $buyer.Body.code -and [int]$buyer.Body.code -eq 0
    }
    Write-TestResult 'API-05' $api05 '携带 token 可访问买家中心数据'

    $orders = Invoke-ApiRequest '订单列表' 'GET' "$baseUrl/api/orders"
    $ordersOk = $orders.Ok -and $orders.Body -and $null -ne $orders.Body.code
    Write-TestResult 'ORDER' $ordersOk '订单列表接口可返回统一结构'

    Write-Host ''
    Write-Host '说明：API-03 跨域访问需要在浏览器 H5 中观察控制台；脚本已覆盖后端连通、统一返回、鉴权和登录态访问。' -ForegroundColor Cyan
    Pause
}

function Check-Paths {
    Write-Host "ROOT=$Script:Root"
    Write-Host "FRONT=$Script:Front"
    Write-Host "BACK=$Script:Back"
    Write-Host "DB_SQL=$Script:DbSql"
    Write-Host "DB_CONFIG=$Script:DbConfig"
    if (-not (Test-Path -LiteralPath (Join-Path $Script:Front 'pages.json'))) { exit 1 }
    if (-not (Test-Path -LiteralPath (Join-Path $Script:Back 'mvnw.cmd'))) { exit 1 }
    if (-not (Test-Path -LiteralPath $Script:DbSql)) { exit 1 }
    Write-Host 'CHECK_OK'
    exit 0
}

Load-DbConfig
Rescan-Tools

if ($args -contains '--check') { Check-Paths }
while ($true) {
    Show-Menu
    $choice = Read-Host '请选择功能编号'
    switch ($choice) {
        '1' { Start-Backend }
        '2' { Open-HBuilderX }
        '3' { Start-H5 }
        '4' { Start-WeChatMiniProgram }
        '5' { Open-MpFolder }
        '6' { Start-Process -FilePath $Script:Root | Out-Null }
        '7' { Rescan-Tools }
        '8' { Setup-Database }
        '9' { Start-DockerStack }
        '10' { Stop-DockerStack }
        '11' { Show-DockerStatus }
        '12' { Reset-DockerVolumes }
        '13' { Restart-DockerStack }
        '0' { exit 0 }
        default { Write-Host '无效选项，请重新输入。' -ForegroundColor Yellow; Start-Sleep -Seconds 1 }
    }
}
