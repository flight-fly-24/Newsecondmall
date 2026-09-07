param(
    [string]$BaseUrl = 'http://localhost',
    [switch]$SkipFixtureSetup
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$collection = Join-Path $PSScriptRoot 'soft-shop-api.postman_collection.json'
$reportDirectory = Join-Path $PSScriptRoot 'reports'
$composeFile = Join-Path $projectRoot 'deploy\docker-compose.yml'
$envFile = Join-Path $projectRoot 'deploy\.env'

if (-not $SkipFixtureSetup) {
    Get-Content -Raw (Join-Path $PSScriptRoot 'fixtures\setup.sql') |
        docker compose --env-file $envFile -f $composeFile exec -T mysql `
            sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw "API test fixture setup failed with exit code $LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Force $reportDirectory | Out-Null

Push-Location $projectRoot
try {
    & npx newman run $collection `
        --env-var "base_url=$BaseUrl" `
        --reporters 'cli,junit,htmlextra' `
        --reporter-junit-export (Join-Path $reportDirectory 'newman-results.xml') `
        --reporter-htmlextra-export (Join-Path $reportDirectory 'newman-report.html')
    if ($LASTEXITCODE -ne 0) {
        throw "Newman API tests failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
