#!/bin/bash
set -e

# Check for Maven
command -v mvn >/dev/null 2>&1 || { echo "maven not installed"; exit 1; }

# Check for Java 17 or 21
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ $JAVA_VERSION != 17* && $JAVA_VERSION != 21* && $JAVA_VERSION != 25* ]]; then
  echo "java 17,21,25 required, found version $JAVA_VERSION"
  exit 1
fi

echo "========================================"
echo "Starting Cuenti Homebanking Application"
echo "Test Mode (H2 In-Memory Database)"
echo "========================================"
echo

mvn spring-boot:run -Dspring-boot.run.profiles=test

