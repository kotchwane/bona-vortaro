#!/bin/bash
# Downloads Reta Vortaro's list of the Vikipedio articles of its words
# into data/vikiref.json, which prevodb reads to link the words to
# Vikipedio. The list is kept in the repository, so that the build needs
# no network; update it with the data of Reta Vortaro (the submodules).
#
# Usage, from anywhere: scripts/update-vikiref.sh

set -eu

cd "$(dirname "$0")"/..

curl --fail --silent --show-error --location \
    https://reta-vortaro.de/revo/inx/vikiref.json \
    --output data/vikiref.json.new
mv data/vikiref.json.new data/vikiref.json
echo "$(grep -c '^\["' data/vikiref.json) words in data/vikiref.json"
