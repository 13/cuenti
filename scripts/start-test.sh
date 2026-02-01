#!/bin/bash

# Script to run the Cuenti Homebanking application with H2 in-memory database
# Useful for testing without PostgreSQL
set -e  # Exit on error

echo "========================================"
echo "Starting Cuenti Homebanking Application"
echo "Test Mode (H2 In-Memory Database)"
echo "========================================"
echo
echo "Database: H2 (in-memory)"
echo "Profile: test"
echo
echo "Note: All data will be lost when the application stops."
echo "For persistent storage, use ./scripts/start.sh instead."
echo
echo "Press Ctrl+C to stop"
echo "========================================"
echo

mvn spring-boot:run -Dspring-boot.run.profiles=test
