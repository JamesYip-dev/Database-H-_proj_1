#!/usr/bin/env bash
set -e
exec "$(dirname "$0")/../shared/run-java.sh" DbmsComparison "$@"
