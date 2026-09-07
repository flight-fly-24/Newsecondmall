param(
    [string]$JMeterHome = $env:JMETER_HOME,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 8080,
    [ValidateSet('http', 'https')]
    [string]$Protocol = 'http',
    [int]$Threads = 100,
    [int]$RampSeconds = 30,
    [int]$DurationSeconds = 300
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JMeterHome)) {
    throw 'JMeter home is required. Set JMETER_HOME or pass -JMeterHome <path>.'
}
if ($Threads -lt 1 -or $RampSeconds -lt 1 -or $DurationSeconds -lt 1) {
    throw 'Threads, RampSeconds and DurationSeconds must all be positive integers.'
}

$jmeter = Join-Path $JMeterHome 'bin\jmeter.bat'
if (-not (Test-Path -LiteralPath $jmeter)) {
    throw "JMeter executable not found: $jmeter"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultRoot = Join-Path $PSScriptRoot "results\catalog-$stamp"
$reportDir = Join-Path $resultRoot 'report'
$plan = Join-Path $PSScriptRoot 'catalog-api-load.jmx'
$resultFile = Join-Path $resultRoot 'result.jtl'
$logFile = Join-Path $resultRoot 'jmeter.log'
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

Write-Host "Target: $Protocol`://$HostName`:$Port/api/products"
Write-Host "Load: $Threads threads, ${RampSeconds}s ramp, ${DurationSeconds}s duration"

& $jmeter -n -t $plan -l $resultFile -j $logFile -e -o $reportDir `
    "-Jhost=$HostName" "-Jport=$Port" "-Jprotocol=$Protocol" `
    "-Jthreads=$Threads" "-Jramp=$RampSeconds" "-Jduration=$DurationSeconds"

if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. See $logFile"
}

Write-Host "JMeter report: $reportDir\index.html"
