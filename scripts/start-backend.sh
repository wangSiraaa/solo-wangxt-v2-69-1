#!/usr/bin/env bash
# 启动 Spring Boot 后端（端口 8080，同时托管前端构建产物）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="$ROOT/tools/jdk"
export PATH="$JAVA_HOME/bin:$ROOT/tools/maven/bin:$PATH"
cd "$ROOT/backend"
if [ -f target/loan-prepay-workbench-1.0.0.jar ]; then
  exec java -jar target/loan-prepay-workbench-1.0.0.jar
else
  exec mvn spring-boot:run
fi
