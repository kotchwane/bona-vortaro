#!/bin/bash

set -eu

cd "$(dirname "$0")"/..

mkdir -p prevodb-build
cd prevodb-build

../prevodb/autogen.sh
# nproc on Linux, sysctl on macOS
make -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu)"

./src/prevodb \
    -i ../extern/revo-fonto \
    -i ../extern/voko-grundo \
    -o ../app/src/main
