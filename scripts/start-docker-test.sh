#!/bin/bash

set -e  # Exit on error

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo "========================================"
echo "Starting Cuenti Homebanking Application"
echo "Test Mode (PostgreSQL Database)"
echo "========================================"
echo
echo "Database: Development"
echo "Profile: Development"
echo

# Check if Docker is installed
if ! command_exists docker; then
    echo "Error: Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "Error: Docker daemon is not running. Please start Docker."
    exit 1
fi

echo "Docker is installed and running."
echo "Press Ctrl+C to stop"
echo "========================================"
echo

# Start Docker Compose in production mode
docker compose -f docker-compose.yml up --build -d
