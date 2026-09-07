#!/usr/bin/env bash
set -uo pipefail
mode="${1:-all}"
case "$mode" in monolith|microservices|all) ;; *) echo "usage: $0 [monolith|microservices|all]"; exit 2;; esac
root="$(cd "$(dirname "$0")/.." && pwd)"
mvn="$root/shopping_back/shopping_back/mvnw"
java_version="$(java -version 2>&1)"
major="$(printf '%s\n' "$java_version" | sed -n 's/.*version "\(1\.\)\?\([0-9][0-9]*\).*/\2/p' | head -n1)"
if [ "$major" != 17 ] && [ "$major" != 21 ]; then echo "Unit tests require JDK 17 or 21. $java_version"; exit 2; fi
echo "Java major: $major"
"$mvn" -version
echo "Maven repository: ${HOME}/.m2/repository"
modules="services/common,services/user-service,services/catalog-service,services/trade-service,services/interaction-service"
status=0
if [ "$mode" = monolith ] || [ "$mode" = all ]; then
  "$mvn" -B -ntp -f "$root/shopping_back/shopping_back/pom.xml" clean test jacoco:report || status=1
fi
if [ "$mode" = microservices ] || [ "$mode" = all ]; then
  "$mvn" -B -ntp -f "$root/pom.xml" clean verify --fail-at-end -Dmaven.test.failure.ignore=true -pl "$modules" -am || status=1
  "$mvn" -B -ntp -f "$root/pom.xml" jacoco:report --fail-at-end -pl "$modules" -am || status=1
  node "$root/tests/unit/summarize-results.mjs" --enforce || status=1
fi
if [ "$status" -ne 0 ]; then echo "Tests failed. For dependency resolution errors retry with -U and check ~/.m2/settings.xml."; fi
exit "$status"
