# Build and push one service image to Huawei SWR, then write that service's newTag
# in k8s/kustomization.yaml. ASCII-only so Windows PowerShell 5.1 can parse it
# (UTF-8 Chinese without BOM is read as GBK and breaks string quotes).
#
# From repo root, after: docker login swr.cn-north-4.myhuaweicloud.com
#   $tag = "xiaoming-20260827"
#   .\ops\push-swr.ps1 $tag backend
#   .\ops\push-swr.ps1 $tag frontend
#   .\ops\push-swr.ps1 $tag trade-service
#   .\ops\push-swr.ps1 $tag backend -Apply
# Add a microservice later: one more entry in $Catalog.

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [string] $Tag,
    [Parameter(Position = 1, Mandatory = $true)]
    [string] $Service,
    [switch] $Apply
)

$ErrorActionPreference = "Stop"

$Registry = "swr.cn-north-4.myhuaweicloud.com"
$Org = "songguo"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot
$CatalogPath = Join-Path $PSScriptRoot "services.conf"
$Catalog = @{}
Get-Content -LiteralPath $CatalogPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("|")
        if ($parts.Count -ne 5 -and $parts.Count -ne 7) { throw "Invalid service catalog entry: $line" }
        $Catalog[$parts[0]] = @{
            Dockerfile = $parts[1]
            Image = $parts[2]
            Deployment = $parts[3]
            Container = $parts[4]
            ModuleDir = $(if ($parts.Count -ge 7) { $parts[5] } else { "" })
            JarName = $(if ($parts.Count -ge 7) { $parts[6] } else { "" })
        }
    }
}

$Tag = $Tag.Trim()
$Service = $Service.Trim().ToLowerInvariant()

if ($Tag -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$') {
    throw "Invalid tag '$Tag'. Use letters, digits, '.', '_' or '-'; do not start with '.' or '-'."
}
if ($Tag -eq "latest") {
    throw "Do not use tag 'latest'. Pick a tag that will not overwrite history."
}
if (-not $Tag.StartsWith("release-")) {
    throw "Release tags must start with 'release-' (for example release-manual-20260828-1030)."
}
if (-not $Catalog.ContainsKey($Service)) {
    $names = ($Catalog.Keys | Sort-Object) -join ", "
    throw "Unknown service '$Service'. Allowed: $names"
}

$item = $Catalog[$Service]
$dockerfile = Join-Path $RepoRoot $item.Dockerfile
if (-not (Test-Path $dockerfile)) {
    throw "Dockerfile not found: $($item.Dockerfile)"
}

$imageName = "${Registry}/${Org}/$($item.Image)"
$image = "${imageName}:${Tag}"
$kustomizePath = Join-Path $RepoRoot "k8s\kustomization.yaml"

function Set-KustomizeNewTag {
    param(
        [string] $Path,
        [string] $FullImageName,
        [string] $NewTag
    )
    $raw = [System.IO.File]::ReadAllText($Path)
    $escaped = [regex]::Escape($FullImageName)
    $pattern = "(?m)(name:\s+$escaped\r?\n\s+newTag:\s+)\S+"
    $updated = [regex]::Replace($raw, $pattern, "`${1}$NewTag", 1)
    if ($updated -eq $raw) {
        throw "Could not update newTag for $FullImageName in kustomization.yaml (check images.name)."
    }
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $updated, $utf8)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not found. Start Docker Desktop first."
}

docker manifest inspect $image *> $null
if ($LASTEXITCODE -eq 0) {
    throw "Refusing to overwrite existing image: $image"
}

Write-Host "Building and pushing $image"
$buildArgs = @(
    "buildx", "build", "--provenance=false", "--sbom=false", "--platform", "linux/amd64",
    "-f", $item.Dockerfile, "-t", $image
)
if ($item.ModuleDir -and $item.JarName) {
    $buildArgs += "--build-arg", "MODULE_DIR=$($item.ModuleDir)"
    $buildArgs += "--build-arg", "JAR_NAME=$($item.JarName)"
}
$buildArgs += "--load", "."
& docker @buildArgs
if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)" }

docker push $image
if ($LASTEXITCODE -ne 0) {
    throw "Push failed (exit $LASTEXITCODE). If the error mentions manifest.json, use this script instead of plain docker build."
}

Set-KustomizeNewTag -Path $kustomizePath -FullImageName $imageName -NewTag $Tag
Write-Host "Updated k8s/kustomization.yaml -> $imageName newTag=$Tag"

$raw = [System.IO.File]::ReadAllText($kustomizePath)
$raw = [regex]::Replace($raw, '(?m)(songguo\.dev/image-tag:\s+).+$', "`${1}`"$Tag`"", 1)
$raw = [regex]::Replace($raw, '(?m)(songguo\.dev/commit-id:\s+).+$', "`${1}`"manual`"", 1)
$raw = [regex]::Replace($raw, '(?m)(songguo\.dev/pipeline-number:\s+).+$', "`${1}`"manual`"", 1)
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($kustomizePath, $raw, $utf8)

if ($Apply) {
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
        throw "-Apply was set but kubectl was not found. Install kubectl and a kubeconfig that can reach ECS k3s."
    }
    Write-Host "kubectl apply -k k8s/"
    kubectl apply -k k8s/
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl apply failed (exit $LASTEXITCODE). YAML is already updated; retry apply later or run it on ECS."
    }
    kubectl -n shop get pods
    if ($LASTEXITCODE -ne 0) { throw "kubectl get pods failed (exit $LASTEXITCODE)" }
} else {
    Write-Host "No -Apply. If this PC has kubeconfig, run: .\ops\push-swr.ps1 $Tag $Service -Apply"
    Write-Host "Or sync k8s/ to ECS and run: sudo kubectl apply -k k8s/"
}

Write-Host ""
Write-Host "Done: $image"
