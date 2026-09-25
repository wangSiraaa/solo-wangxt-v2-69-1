#!/usr/bin/env bash
# 启动用户态 PostgreSQL 16（无需 root）。数据目录：tools/pgdata
# 首次运行会初始化数据目录并创建 loandb 数据库。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGBIN="$ROOT/tools/pgsql/bin"
PGDATA="$ROOT/tools/pgdata"

if [ ! -d "$PGDATA" ]; then
  echo "初始化 PostgreSQL 数据目录…"
  "$PGBIN/initdb" -D "$PGDATA" -U postgres --auth=trust --encoding=UTF8 >/dev/null
  # 单用户模式建库（zonky 精简包不含 psql）
  "$PGBIN/postgres" --single -D "$PGDATA" postgres <<< "CREATE DATABASE loandb" >/dev/null
fi

if "$PGBIN/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then
  echo "PostgreSQL 已在运行"
else
  "$PGBIN/pg_ctl" -D "$PGDATA" -l "$ROOT/tools/pgdata.log" -o "-p 5432 -k /tmp" start >/dev/null
fi
echo "PostgreSQL ready on localhost:5432 (database: loandb, user: postgres, password: postgres)"
