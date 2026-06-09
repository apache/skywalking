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

# Shared docker compose helpers for Airflow cluster e2e.
# infra-e2e uses project name {workspace}_e2e (e.g. skywalking_e2e), not the case folder name.

COMPOSE_FILE="${COMPOSE_FILE:-test/e2e-v2/cases/airflow/cluster/docker-compose.yml}"
COMPOSE_OVERRIDE="${COMPOSE_OVERRIDE:-}"

resolve_compose_project() {
  if [[ -n "${COMPOSE_PROJECT_NAME:-}" ]]; then
    echo "${COMPOSE_PROJECT_NAME}"
    return
  fi
  local scheduler_container
  scheduler_container="$(docker ps --filter 'name=-airflow-scheduler-' --format '{{.Names}}' | head -1)"
  if [[ -n "${scheduler_container}" ]]; then
    echo "${scheduler_container%-airflow-scheduler-*}"
    return
  fi
  echo "skywalking_e2e"
}

COMPOSE_PROJECT_NAME="$(resolve_compose_project)"

dc() {
  if [[ -n "${COMPOSE_OVERRIDE}" ]]; then
    docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" -f "${COMPOSE_OVERRIDE}" "$@"
  else
    docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
  fi
}
