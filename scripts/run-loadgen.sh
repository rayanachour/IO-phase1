#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/java-loadgen"
rm -f dependency-reduced-pom.xml
mvn -q -DskipTests package
java -jar target/java-loadgen-0.1.0-shaded.jar --config="$ROOT/config/config.yml" --profile="${1:-baseline}"
