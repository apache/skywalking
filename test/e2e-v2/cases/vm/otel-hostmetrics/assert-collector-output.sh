#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -euo pipefail

CID="$(
  docker ps \
    --filter 'label=com.docker.compose.service=vm-service' \
    --filter 'status=running' \
    --format '{{.ID}}' \
    | head -n 1
)"

if [ -z "${CID}" ]; then
  echo "vm-service container not found" >&2
  exit 1
fi

TMPDIR="$(mktemp -d)"
trap 'rm -rf "${TMPDIR}"' EXIT

docker exec "${CID}" cat /tmp/process-linux-raw.log \
  > "${TMPDIR}/process-linux.log"

docker exec "${CID}" cat /tmp/windows-host-raw.log \
  > "${TMPDIR}/windows-host.log"

docker exec "${CID}" cat /tmp/windows-process-raw.log \
  > "${TMPDIR}/windows-process.log"


# ---------------------------------------------------------------------------
# Linux process pre-OTLP contract.
#
# For every observed process.count metric block we require the Collector-side
# result to contain one datapoint with value 3 and the corrected {process} unit.
# A raw value 1 would reproduce the reviewer-reported regression.
# ---------------------------------------------------------------------------
awk '
function finish_block() {
    if (!in_count) {
        return
    }

    if (points == 1 && value3 && unit_process) {
        good = 1
    }

    if (value1) {
        bad = 1
    }

    in_count = 0
}

/-> Name: process.count[[:space:]]*$/ {
    finish_block()
    in_count = 1
    points = 0
    value3 = 0
    value1 = 0
    unit_process = 0
    next
}

in_count && /^[[:space:]]*Metric #[0-9]+/ {
    finish_block()
    next
}

in_count && /NumberDataPoints #[0-9]+/ {
    points++
}

in_count && /-> Unit: \{process\}/ {
    unit_process = 1
}

in_count && /Value:[[:space:]]+3(\.0+)?[[:space:]]*$/ {
    value3 = 1
}

in_count && /Value:[[:space:]]+1(\.0+)?[[:space:]]*$/ {
    value1 = 1
}

END {
    finish_block()
    exit(good && !bad ? 0 : 1)
}
' "${TMPDIR}/process-linux.log"


# ---------------------------------------------------------------------------
# Windows host normalization:
# native system.cpu.time state -> canonical mode.
# ---------------------------------------------------------------------------
grep -q -- '-> Name: system.cpu.time' \
  "${TMPDIR}/windows-host.log"

grep -q -- '-> mode: Str(idle)' \
  "${TMPDIR}/windows-host.log"

grep -q -- '-> mode: Str(user)' \
  "${TMPDIR}/windows-host.log"

if grep -q -- '-> state:' "${TMPDIR}/windows-host.log"; then
  echo "Windows system.cpu.time still contains state attribute" >&2
  exit 1
fi


# ---------------------------------------------------------------------------
# Windows process normalization:
# native process.handles -> canonical process.open_handles.
# ---------------------------------------------------------------------------
grep -q -- '-> Name: process.open_handles' \
  "${TMPDIR}/windows-process.log"

if grep -q -- '-> Name: process.handles[[:space:]]*$' \
  "${TMPDIR}/windows-process.log"; then
  echo "Native process.handles survived normalization" >&2
  exit 1
fi


cat <<'YAML'
collector_process_count:
  datapoints_per_export: 1
  value: 3
  unit: "{process}"
windows_normalization:
  cpu_state_to_mode: true
  process_handles_to_open_handles: true
YAML
