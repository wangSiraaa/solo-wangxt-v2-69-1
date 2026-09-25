#!/usr/bin/env bash
# 启动 Angular 开发服务器（端口 4200，/api 代理到 8080）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/frontend"
exec npx ng serve --proxy-config proxy.conf.json
