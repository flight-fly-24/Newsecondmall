param(
    [string]$JMeterHome = $env:JMETER_HOME,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 8080,
    [ValidateSet('http', 'https')]
    [string]$Protocol = 'http',
    [int]$Threads = 20,
    [int]$RampSeconds = 20,
    [int]$DurationSeconds = 60
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JMeterHome)) {
    throw 'JMeter home is required. Set JMETER_HOME or pass -JMeterHome <path>.'
}

$jmeter = Join-Path $JMeterHome 'bin\jmeter.bat'
if (-not (Test-Path -LiteralPath $jmeter)) {
    throw "JMeter executable not found: $jmeter"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultRoot = Join-Path $PSScriptRoot "results\$stamp"
$reportDir = Join-Path $resultRoot 'report'
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

$plan = Join-Path $PSScriptRoot 'shop-api-load.jmx'
$resultFile = Join-Path $resultRoot 'result.jtl'
$logFile = Join-Path $resultRoot 'jmeter.log'

& $jmeter -n -t $plan -l $resultFile -j $logFile -e -o $reportDir `
    "-Jhost=$HostName" "-Jport=$Port" "-Jprotocol=$Protocol" `
    "-Jthreads=$Threads" "-Jramp=$RampSeconds" "-Jduration=$DurationSeconds"

if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. See $logFile"
}

Write-Host "JMeter report: $reportDir\index.html"
