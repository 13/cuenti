#!/bin/bash

set -e  # Exit on error

echo "========================================"
echo "Starting Cuenti Homebanking Application"
echo "Production Mode (PostgreSQL Database)"
echo "========================================"
echo
echo "Database: Production"
echo "Profile: Production"
echo
echo "Press Ctrl+C to stop"
echo "========================================"
echo

docker compose -f docker-compose.prod.yml up --build -d
