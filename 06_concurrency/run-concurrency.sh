#!/usr/bin/env bash
set -euo pipefail
exec "$(dirname "$0")/../shared/run-java.sh" ConcurrencyExperiment "$@"
