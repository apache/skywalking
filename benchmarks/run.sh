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

# Benchmark runner — single entry point with two modes.
#
# Mode 1: Setup environment only
#   ./benchmarks/run.sh setup <env-name>
#
# Mode 2: Setup environment + run benchmark case
#   ./benchmarks/run.sh run <env-name> <case-name>
#
# Available environments:  (ls benchmarks/envs-setup/)
# Available cases:         (ls benchmarks/cases/)
#
# Examples:
#   ./benchmarks/run.sh setup cluster_oap-banyandb
#   ./benchmarks/run.sh run   cluster_oap-banyandb thread-analysis

set -euo pipefail

BENCHMARKS_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

# Load cluster name from env config (for cleanup)
source "$BENCHMARKS_DIR/env" 2>/dev/null || true
CLUSTER_NAME="${CLUSTER_NAME:-benchmark-cluster}"

# Cleanup: delete Kind cluster and prune Docker resources.
# Called automatically after 'run' mode completes (success or failure).
# Deleting the Kind cluster reclaims the largest chunk of disk (copied images).
# We only prune dangling images (not -a) to preserve locally built images.
cleanup_cluster() {
    echo ""
    echo ">>> Cleaning up..."
    if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
        echo "  Deleting Kind cluster '${CLUSTER_NAME}'..."
        kind delete cluster --name "$CLUSTER_NAME" 2>&1 || true
    fi
    echo "  Pruning dangling Docker resources..."
    docker image prune -f 2>&1 | tail -1 || true
    docker volume prune -f 2>&1 | tail -1 || true
    echo ">>> Cleanup complete."
}

usage() {
    echo "Usage:"
    echo "  $0 setup <env-name>              Setup environment only"
    echo "  $0 run   <env-name> <case-name>  Setup environment + run benchmark case"
    echo ""
    echo "Available environments:"
    for d in "$BENCHMARKS_DIR"/envs-setup/*/; do
        [ -d "$d" ] && echo "  $(basename "$d")"
    done
    echo ""
    echo "Available cases:"
    for d in "$BENCHMARKS_DIR"/cases/*/; do
        [ -d "$d" ] && echo "  $(basename "$d")"
    done
    exit 1
}

if [ $# -lt 2 ]; then
    usage
fi

MODE="$1"
ENV_NAME="$2"

ENV_DIR="$BENCHMARKS_DIR/envs-setup/$ENV_NAME"
if [ ! -d "$ENV_DIR" ] || [ ! -f "$ENV_DIR/setup.sh" ]; then
    echo "ERROR: Environment '$ENV_NAME' not found."
    echo "  Expected: $ENV_DIR/setup.sh"
    exit 1
fi

case "$MODE" in
    setup)
        export REPORT_DIR="$BENCHMARKS_DIR/reports/$ENV_NAME/$TIMESTAMP"
        mkdir -p "$REPORT_DIR"

        # Clean up on failure only (user inspects the env on success)
        setup_cleanup_on_error() {
            local rc=$?
            if [ $rc -ne 0 ]; then
                cleanup_cluster
            fi
        }
        trap setup_cleanup_on_error EXIT

        echo "=== Setting up environment: $ENV_NAME ==="
        echo "  Report dir: $REPORT_DIR"
        echo ""

        "$ENV_DIR/setup.sh"

        echo ""
        echo "=== Environment ready ==="
        echo "  Context file: $REPORT_DIR/env-context.sh"
        echo ""
        echo "To run a benchmark case against this environment:"
        echo "  $0 run $ENV_NAME <case-name>"
        echo "  — or directly —"
        echo "  benchmarks/cases/<case-name>/run.sh $REPORT_DIR/env-context.sh"
        echo ""
        echo "To tear down when done:"
        echo "  kind delete cluster --name $CLUSTER_NAME"
        ;;

    run)
        if [ $# -lt 3 ]; then
            echo "ERROR: 'run' mode requires both <env-name> and <case-name>."
            echo ""
            usage
        fi
        CASE_NAME="$3"

        CASE_DIR="$BENCHMARKS_DIR/cases/$CASE_NAME"
        if [ ! -d "$CASE_DIR" ] || [ ! -f "$CASE_DIR/run.sh" ]; then
            echo "ERROR: Case '$CASE_NAME' not found."
            echo "  Expected: $CASE_DIR/run.sh"
            exit 1
        fi

        export REPORT_DIR="$BENCHMARKS_DIR/reports/$ENV_NAME/$CASE_NAME/$TIMESTAMP"
        mkdir -p "$REPORT_DIR"

        # Always clean up after run mode (success or failure)
        trap cleanup_cluster EXIT

        echo "=== Benchmark: $CASE_NAME on $ENV_NAME ==="
        echo "  Report dir: $REPORT_DIR"
        echo ""

        # Phase 1: Setup environment
        echo ">>> Setting up environment: $ENV_NAME"
        "$ENV_DIR/setup.sh"

        CONTEXT_FILE="$REPORT_DIR/env-context.sh"
        if [ ! -f "$CONTEXT_FILE" ]; then
            echo "ERROR: setup.sh did not produce $CONTEXT_FILE"
            exit 1
        fi

        # Phase 2: Run benchmark case
        echo ""
        echo ">>> Running case: $CASE_NAME"
        "$CASE_DIR/run.sh" "$CONTEXT_FILE"
        ;;

    *)
        echo "ERROR: Unknown mode '$MODE'."
        echo ""
        usage
        ;;
esac
