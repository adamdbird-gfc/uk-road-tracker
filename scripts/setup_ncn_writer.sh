#!/usr/bin/env bash
# Reproduce the reviewed writer only. Publication reuses its pinned archive.
set -euo pipefail
writer_root="${RUNNER_TEMP:?}/ncn-reviewed-writer"
mkdir -p "$writer_root/lib"
cd "$writer_root"
curl --fail --silent --show-error --location https://www.sqlite.org/2026/sqlite-amalgamation-3530100.zip --output sqlite.zip
curl --fail --silent --show-error --location https://zlib.net/fossils/zlib-1.3.2.tar.gz --output zlib.tar.gz
sha256sum --check <<'PINS'
36ad6e7f38540a3b21a2ac36340833f0a9e426bc1c752751c3ba669466827eae  sqlite.zip
bb329a0a2cd0274d05519d61c667c062e06990d72e125ee2dfa8de64f0119d16  zlib.tar.gz
PINS
unzip -q sqlite.zip
gcc -O2 -fPIC -shared -DSQLITE_ENABLE_COLUMN_METADATA -Wl,-soname,libsqlite3.so.0 sqlite-amalgamation-3530100/sqlite3.c -o lib/libsqlite3.so.0 -lpthread -ldl -lm
tar -xzf zlib.tar.gz
cd zlib-1.3.2
./configure --prefix="$writer_root" >/dev/null
make -j2 >/dev/null
make install >/dev/null
printf 'LD_LIBRARY_PATH=%s/lib:%s\n' "$writer_root" "${LD_LIBRARY_PATH:-}" >> "${GITHUB_ENV:?}"
