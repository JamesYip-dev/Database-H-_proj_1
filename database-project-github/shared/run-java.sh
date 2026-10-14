#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
if [[ -f .local-login ]]; then source .local-login; fi
mkdir -p work/classes
# 自动收集各实验源文件，实验目录替换后无需维护固定目录列表。
sources=(shared/*.java [0-9][0-9]_*/src/*.java)
javac -encoding UTF-8 -cp 'lib/*' -d work/classes "${sources[@]}"
java -Djava.util.logging.config.file=shared/jdbc-logging.properties -cp 'lib/*:work/classes' "$@"
