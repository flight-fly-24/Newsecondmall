param([ValidateSet("monolith", "microservices", "all")][string]$Mode = "all")
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $root "shopping_back\shopping_back\mvnw.cmd"
# $javaText = (& java -version 2>&1 | Out-String)
$oldErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javaText = (& java -version 2>&1 | Out-String)
$ErrorActionPreference = $oldErrorActionPreference
# -----
$majorMatch = [regex]::Match($javaText, 'version "(?:1\.)?(\d+)')
if (-not $majorMatch.Success -or @("17", "21") -notcontains $majorMatch.Groups[1].Value) {
    throw "Unit tests require JDK 17 or 21. Current output: $javaText"
}
Write-Host "Java major: $($majorMatch.Groups[1].Value)"
& $mvn -version
Write-Host "Maven repository: $env:USERPROFILE\.m2\repository"

function Run-Monolith {
    & $mvn -B -ntp -f (Join-Path $root "shopping_back\shopping_back\pom.xml") clean test jacoco:report
    $script:monolithStatus = $LASTEXITCODE
}
function Run-Microservices {
    $modules = "services/common,services/user-service,services/catalog-service,services/trade-service,services/interaction-service"
    & $mvn -B -ntp -f (Join-Path $root "pom.xml") clean verify --fail-at-end "-Dmaven.test.failure.ignore=true" -pl $modules -am
    $testStatus = $LASTEXITCODE
    & $mvn -B -ntp -f (Join-Path $root "pom.xml") jacoco:report --fail-at-end -pl $modules -am
    & node (Join-Path $root "tests\unit\summarize-results.mjs") --enforce
    $gateStatus = $LASTEXITCODE
    $script:microservicesStatus = if ($testStatus -ne 0 -or $gateStatus -ne 0) { 1 } else { 0 }
}

$status = 0
if ($Mode -in @("monolith", "all")) { Run-Monolith; if ($script:monolithStatus -ne 0) { $status = 1 } }
if ($Mode -in @("microservices", "all")) { Run-Microservices; if ($script:microservicesStatus -ne 0) { $status = 1 } }
if ($status -ne 0) {
    Write-Host "Tests failed. If Maven could not resolve dependencies, retry once with the wrapper and -U and check ~/.m2/settings.xml."
}
exit $status
