$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Push-Location shopping_front
npm ci
npm run build:h5
Pop-Location

Push-Location shopping_back/shopping_back
./mvnw.cmd package -DskipTests
Pop-Location

$env:DB_PASSWORD = 'local-test-app-password'
$env:MYSQL_ROOT_PASSWORD = 'local-test-root-password'
$env:PUBLIC_ORIGIN = 'http://localhost'
$env:HTTP_PORT = '8088'
$env:IMAGE_TAG = "ci-$((git rev-parse --short HEAD))"
docker compose -f deploy/docker-compose.yml up -d --build
& "$root/tests/blackbox/smoke.ps1" -BaseUrl 'http://127.0.0.1:8088'
