param(
    [string]$Namespace = 'shop',
    [string]$Deployment = 'catalog-service',
    [string]$Hpa = 'catalog-service',
    [int]$IntervalSeconds = 10,
    [int]$DurationSeconds = 600,
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl is not installed or is not available on PATH.'
}
if ($IntervalSeconds -lt 1 -or $DurationSeconds -lt 1) {
    throw 'IntervalSeconds and DurationSeconds must be positive integers.'
}
if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputFile = Join-Path $PSScriptRoot "..\tests\performance\results\catalog-hpa-$stamp.csv"
}

$outputPath = [System.IO.Path]::GetFullPath($OutputFile)
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
'timestamp,desiredReplicas,currentReplicas,readyReplicas,currentCpu,targetCpu,podCpu' | Set-Content -LiteralPath $outputPath -Encoding UTF8

$deadline = (Get-Date).AddSeconds($DurationSeconds)
Write-Host "Recording HPA metrics every ${IntervalSeconds}s for ${DurationSeconds}s"
Write-Host "CSV output: $outputPath"

while ((Get-Date) -lt $deadline) {
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $deploymentJson = & kubectl -n $Namespace get deployment $Deployment -o json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw "Could not read deployment $Namespace/$Deployment." }
    $hpaJson = & kubectl -n $Namespace get hpa $Hpa -o json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw "Could not read HPA $Namespace/$Hpa." }

    $currentCpu = if ($hpaJson.status.currentMetrics.Count -gt 0) {
        $hpaJson.status.currentMetrics[0].resource.current.averageUtilization
    } else { '' }
    $targetCpu = $hpaJson.spec.metrics[0].resource.target.averageUtilization
    $topOutput = & kubectl -n $Namespace top pods -l "app.kubernetes.io/name=$Deployment" --no-headers 2>$null
    $podCpu = if ($LASTEXITCODE -eq 0) { (($topOutput | ForEach-Object { ($_ -split '\s+')[1] }) -join ';') } else { '' }

    $row = '{0},{1},{2},{3},{4},{5},"{6}"' -f `
        $timestamp, $hpaJson.status.desiredReplicas, $hpaJson.status.currentReplicas, `
        $deploymentJson.status.readyReplicas, $currentCpu, $targetCpu, $podCpu
    Add-Content -LiteralPath $outputPath -Value $row -Encoding UTF8
    Write-Host $row
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "HPA recording completed: $outputPath"
