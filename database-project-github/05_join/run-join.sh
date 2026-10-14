#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 05_join/prepare-data.py
exec ./shared/run-java.sh JoinExperiment "$@"
