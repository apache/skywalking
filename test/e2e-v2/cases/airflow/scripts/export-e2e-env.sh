#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# infra-e2e reads ~/.skywalking-infra-e2e/.env for ${oap_host} / ${oap_12800} substitution in verify.
# On Windows the runner may not populate that file; export ports from the running compose project.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=cluster-compose-env.sh
source "${SCRIPT_DIR}/cluster-compose-env.sh"

OAP_PORT="$(dc port oap 12800 | cut -d: -f2)"
ENV_DIR="${HOME}/.skywalking-infra-e2e"
ENV_FILE="${ENV_DIR}/.env"

mkdir -p "${ENV_DIR}"
cat > "${ENV_FILE}" <<EOF
oap_host=localhost
oap_12800=${OAP_PORT}
PATH=/tmp/skywalking-infra-e2e/bin:${PATH}
EOF

echo "Wrote ${ENV_FILE} (oap_12800=${OAP_PORT})"
