#!/usr/bin/env bash
set -euo pipefail
cd /workspace/tools/dl
echo "== JDK =="
curl -sL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
mkdir -p /workspace/tools/jdk && tar xzf jdk.tar.gz -C /workspace/tools/jdk --strip-components=1
echo "== Maven =="
curl -sL -o maven.tar.gz "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz" \
 || curl -sL -o maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
mkdir -p /workspace/tools/maven && tar xzf maven.tar.gz -C /workspace/tools/maven --strip-components=1
echo "== PostgreSQL =="
curl -sL -o pg.jar "https://repo1.maven.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-linux-arm64v8/16.15.0/embedded-postgres-binaries-linux-arm64v8-16.15.0.jar"
mkdir -p pgx && cd pgx && /workspace/tools/jdk/bin/jar xf ../pg.jar && ls
TXZ=$(find . -name '*.txz' | head -1)
mkdir -p /workspace/tools/pgsql && tar xJf "$TXZ" -C /workspace/tools/pgsql
echo "== done =="
/workspace/tools/jdk/bin/java -version
/workspace/tools/pgsql/bin/postgres --version
