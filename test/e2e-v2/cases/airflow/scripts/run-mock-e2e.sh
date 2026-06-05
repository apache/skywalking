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

# Local mock e2e (OAP + BanyanDB + OTLP JSON replay). Avoids infra-e2e CRLF env issues on
# Windows and uses a locally available Temurin JRE image for the sender sidecar.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CASE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "${REPO_ROOT}"

export SW_BANYANDB_COMMIT="${SW_BANYANDB_COMMIT:-84b919efca3fee3d51df9e97a734a9f10ae6f1d2}"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-airflow_mock_e2e}"
export PATH="/tmp/skywalking-infra-e2e/bin:/usr/bin:/bin:${PATH}"

COMPOSE_FILE="${CASE_DIR}/docker-compose.yml"
LOCAL_OVERRIDE="${CASE_DIR}/docker-compose.mock-local.yml"
MOCK_SENDER_POM="${REPO_ROOT}/test/e2e-v2/cases/airflow/mock-sender/pom.xml"
E2E_LIBS_POM="${REPO_ROOT}/test/e2e-v2/java-test-service/pom.xml"
JAR="${REPO_ROOT}/test/e2e-v2/cases/airflow/mock-sender/target/airflow-mock-sender-2.0.0.jar"

dc() {
  docker compose -f "${COMPOSE_FILE}" -f "${LOCAL_OVERRIDE}" -p "${COMPOSE_PROJECT_NAME}" "$@"
}

echo "=== Airflow mock e2e (project ${COMPOSE_PROJECT_NAME}) ==="

build_mock_sender() {
  echo "Building airflow-mock-sender dependencies..."
  ./mvnw -B -q -f "${E2E_LIBS_POM}" -pl opentelemetry-proto -am install \
    -Dcheckstyle.skip=true -Dgpg.skip=true -Dmaven.test.skip=true
  echo "Building airflow-mock-sender..."
  ./mvnw -B -q -f "${MOCK_SENDER_POM}" clean flatten:flatten package \
    -Dcheckstyle.skip=true -Dgpg.skip=true
}

if [[ ! -f "${JAR}" ]]; then
  build_mock_sender
else
  echo "Refreshing airflow-mock-sender jar..."
  build_mock_sender
fi

docker compose -f "${COMPOSE_FILE}" -f "${LOCAL_OVERRIDE}" -p "${COMPOSE_PROJECT_NAME}" down --remove-orphans 2>/dev/null || true
dc up -d

echo "Waiting for OAP and mock sender..."
for _ in $(seq 1 90); do
  if dc exec -T oap bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/11800' 2>/dev/null &&
    dc exec -T sender sh -c 'nc -nz 127.0.0.1 9093' 2>/dev/null; then
    break
  fi
  sleep 5
done

/usr/bin/bash test/e2e-v2/script/prepare/setup-e2e-shell/install.sh swctl
/usr/bin/bash test/e2e-v2/script/prepare/setup-e2e-shell/install.sh yq

trigger_metrics() {
  dc exec -T sender sh -c \
    'wget -q -O /dev/null http://127.0.0.1:9093/otel-metrics/send 2>/dev/null || curl -sf http://127.0.0.1:9093/otel-metrics/send'
}

# Replay like infra-e2e trigger (continuous during verify for PT1M increase metrics).
trigger_loop() {
  while true; do
    trigger_metrics || true
    sleep 3
  done
}

echo "Seeding OTLP metrics..."
trigger_loop &
TRIGGER_PID=$!
sleep 120

VERIFY_RETRIES=20 VERIFY_INTERVAL_SECONDS=10 \
  /usr/bin/bash "${SCRIPT_DIR}/verify-mock-e2e.sh" || VERIFY_EXIT=$?

kill "${TRIGGER_PID}" 2>/dev/null || true
wait "${TRIGGER_PID}" 2>/dev/null || true

if [[ "${VERIFY_EXIT:-0}" -ne 0 ]]; then
  exit "${VERIFY_EXIT}"
fi

echo "=== Mock e2e PASSED ==="
