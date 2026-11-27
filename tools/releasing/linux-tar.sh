#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Packs and checks release tarballs with GNU tar in a pinned Linux image, never with the host's tar.
# On macOS, tar can store a file's extended attributes (com.apple.provenance) as pax headers, and as
# an AppleDouble `._*` entry beside the file unless COPYFILE_DISABLE is set. GNU tar on Linux warns
# about each such header and unpacks the `._*` entries as stray files (apache/skywalking#14271). The
# container does not see macOS attributes and GNU tar creates no `._*` entries; `verify` catches a
# `._*` file that was already on disk. The release scripts of apache/skywalking, skywalking-java,
# skywalking-mcp, skywalking-swck, skywalking-helm, skywalking-client-js and skywalking-nodejs
# share this file; keep every copy identical.
#
# Usage:
#   linux-tar.sh check
#       Fails unless Docker is installed and its daemon is running. Run it before a long build.
#   linux-tar.sh pack <dir> <tar arguments...>
#       Runs GNU tar in <dir>. Only <dir> is visible in the container, so every path in the
#       arguments is relative to it and stays inside it.
#   linux-tar.sh verify <tarball>...
#       Unpacks each tarball with GNU tar, as a user on Linux does, and fails on any warning or
#       any AppleDouble `._*` entry.

set -euo pipefail

IMAGE="debian:bookworm-slim@sha256:e67eeead736ff7bbe258ef088bff683635c561b1fcfefad0806c1df4ab49ce0e"

check() {
    if ! command -v docker > /dev/null; then
        echo "ERROR: docker is not installed. Release tarballs are packed in a Linux container." >&2
        exit 1
    fi
    if ! docker info > /dev/null 2>&1; then
        echo "ERROR: the Docker daemon is not running. Release tarballs are packed in a Linux container." >&2
        exit 1
    fi
}

pack() {
    local dir
    dir=$(cd "$1" && pwd)
    shift
    docker run --rm --user "$(id -u):$(id -g)" -v "${dir}:/work" -w /work "${IMAGE}" tar "$@"
}

verify() {
    local tarball dir name
    for tarball in "$@"; do
        dir=$(cd "$(dirname "${tarball}")" && pwd)
        name=$(basename "${tarball}")
        if ! docker run --rm --user "$(id -u):$(id -g)" -v "${dir}:/work:ro" "${IMAGE}" sh -c '
                mkdir /tmp/unpacked
                tar -xzf "/work/$1" -C /tmp/unpacked 2> /tmp/warnings || { cat /tmp/warnings; exit 1; }
                if [ -s /tmp/warnings ]; then cat /tmp/warnings; exit 1; fi
                find /tmp/unpacked -name "._*" > /tmp/appledouble || exit 1
                if [ -s /tmp/appledouble ]; then cat /tmp/appledouble; exit 1; fi' sh "${name}"; then
            echo "ERROR: ${name} does not unpack cleanly with GNU tar on Linux (output above)." >&2
            exit 1
        fi
        echo "${name}: unpacks cleanly with GNU tar on Linux."
    done
}

usage() {
    echo "Usage: $0 check | pack <dir> <tar arguments...> | verify <tarball>..." >&2
    exit 2
}

command="${1:-}"
[ $# -gt 0 ] && shift
case "${command}" in
    check)  [ $# -eq 0 ] || usage; check ;;
    pack)   [ $# -ge 2 ] || usage; pack "$@" ;;
    verify) [ $# -ge 1 ] || usage; verify "$@" ;;
    *)      usage ;;
esac
