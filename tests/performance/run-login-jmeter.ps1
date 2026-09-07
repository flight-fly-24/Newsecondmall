param([string]$JMeterHome=$env:JMETER_HOME,[string]$HostName='127.0.0.1',[int]$Port=8080,[int]$Threads=20,[int]$RampSeconds=20,[int]$DurationSeconds=120,[string]$Username='demo',[string]$Password='demo123')
$ErrorActionPreference='Stop'
$jmeter=Join-Path $JMeterHome 'bin\jmeter.bat'
if ([string]::IsNullOrWhiteSpace($JMeterHome) -or -not (Test-Path $jmeter)) { throw 'Pass a valid -JMeterHome.' }
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$root=Join-Path $PSScriptRoot "results\login-$stamp"; $report=Join-Path $root 'report'
New-Item -ItemType Directory -Path $root -Force | Out-Null
& $jmeter -n -t (Join-Path $PSScriptRoot 'login-api-load.jmx') -l (Join-Path $root 'result.jtl') -j (Join-Path $root 'jmeter.log') -e -o $report "-Jhost=$HostName" "-Jport=$Port" "-Jthreads=$Threads" "-Jramp=$RampSeconds" "-Jduration=$DurationSeconds" "-Jusername=$Username" "-Jpassword=$Password"
if ($LASTEXITCODE -ne 0) { throw "JMeter failed; see $root\jmeter.log" }
Write-Host "JMeter report: $report\index.html"
