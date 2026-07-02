#!/usr/bin/env bash
# Compiles and runs the Sushi Typing backend, which also serves the frontend.
set -euo pipefail

cd "$(dirname "$0")/backend"

mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out com.typingsushi.Main
