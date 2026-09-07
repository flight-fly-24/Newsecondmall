param(
    [string]$Namespace = 'shop',
    [string]$Deployment = 'catalog-service',
    [string]$Hpa = 'catalog-service'
)

$ErrorActionPreference = 'Stop'

function Invoke-Kubectl {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl command failed: kubectl $($Arguments -join ' ')"
    }
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl is not installed or is not available on PATH.'
}

Write-Host '1/6 Checking current Kubernetes context...'
Invoke-Kubectl config current-context

Write-Host '2/6 Checking node readiness...'
Invoke-Kubectl get nodes

Write-Host '3/6 Checking Metrics Server...'
Invoke-Kubectl -n kube-system get deployment metrics-server

Write-Host '4/6 Checking Metrics API...'
Invoke-Kubectl top nodes

Write-Host "5/6 Checking deployment $Namespace/$Deployment..."
Invoke-Kubectl -n $Namespace get deployment $Deployment
Invoke-Kubectl -n $Namespace rollout status "deployment/$Deployment" --timeout=10s

$requestCpu = & kubectl -n $Namespace get deployment $Deployment `
    -o 'jsonpath={.spec.template.spec.containers[0].resources.requests.cpu}'
if ($LASTEXITCODE -ne 0) {
    throw "Could not read CPU request from deployment $Namespace/$Deployment."
}
if ([string]::IsNullOrWhiteSpace($requestCpu)) {
    throw "Deployment $Namespace/$Deployment has no CPU request; CPU utilization HPA cannot work correctly."
}
Write-Host "CPU request: $requestCpu"

Write-Host "6/6 Checking HPA $Namespace/$Hpa..."
& kubectl -n $Namespace get hpa $Hpa
if ($LASTEXITCODE -ne 0) {
    Write-Warning "HPA $Namespace/$Hpa is not deployed yet. Apply k8s/catalog-service-hpa.yaml before the experiment."
} else {
    Invoke-Kubectl -n $Namespace describe hpa $Hpa
}

Write-Host 'HPA prerequisite check completed.'
