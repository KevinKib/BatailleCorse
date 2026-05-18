#!/bin/bash

cleanup() {
  echo "[dev] Shutting down..."
  kill $MVN_PID 2>/dev/null
  exit 0
}
trap cleanup SIGTERM SIGINT

echo "[dev] Starting Spring Boot..."
mvn spring-boot:run &
MVN_PID=$!

echo "[dev] Watching src/ for changes..."
while true; do
  inotifywait -r -e close_write,moved_to,create,delete src/ 2>/dev/null || sleep 2
  echo "[dev] Change detected — compiling..."
  sleep 0.5
  mvn compile -q || true
done
