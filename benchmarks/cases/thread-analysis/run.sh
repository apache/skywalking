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

# Benchmark case: Thread dump analysis.
#
# Collects periodic thread dumps from OAP pods, monitors metrics in the
# background, and produces a thread pool analysis report.
#
# Usage:
#   ./run.sh <env-context-file>
#
# The env-context file is produced by an env-setup script (e.g.,
# envs-setup/istio-cluster_oap-banyandb/setup.sh).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ] || [ ! -f "$1" ]; then
    echo "Usage: $0 <env-context-file>"
    echo "  env-context-file is produced by an envs-setup/*/setup.sh script."
    exit 1
fi

# Source environment context
source "$1"

# Configurable via env vars
DUMP_COUNT="${DUMP_COUNT:-5}"
DUMP_INTERVAL="${DUMP_INTERVAL:-60}"

log() { echo "[$(date +%H:%M:%S)] $*"; }

cleanup_pids() {
    for pid in "${BG_PIDS[@]:-}"; do
        kill "$pid" 2>/dev/null || true
    done
}
trap cleanup_pids EXIT
BG_PIDS=()

# Verify we can reach OAP
if ! command -v swctl &>/dev/null; then
    echo "ERROR: swctl not found. Please install it first."
    exit 1
fi

log "=== Thread Analysis Benchmark ==="
log "Environment: $ENV_NAME"
log "OAP: ${OAP_HOST}:${OAP_PORT}"
log "Namespace: $NAMESPACE"
log "Dump config: $DUMP_COUNT rounds, ${DUMP_INTERVAL}s apart"
log "Report dir: $REPORT_DIR"

#############################################################################
# Metrics monitor (background)
#############################################################################
log "--- Starting metrics monitor (every 10s) ---"

OAP_BASE_URL="http://${OAP_HOST}:${OAP_PORT}/graphql"

metrics_monitor() {
    local round=0
    while true; do
        round=$((round + 1))
        local out="$REPORT_DIR/metrics-round-${round}.yaml"
        {
            echo "--- round: $round  time: $(date -u +%Y-%m-%dT%H:%M:%SZ) ---"
            echo ""
            echo "# services"
            swctl --display yaml --base-url="$OAP_BASE_URL" service ls 2>/dev/null || echo "ERROR"
            echo ""
            # Query the first discovered service for metrics
            FIRST_SVC=$(swctl --display yaml --base-url="$OAP_BASE_URL" service ls 2>/dev/null \
                | grep '  name:' | head -1 | sed 's/.*name: //' || echo "")
            if [ -n "$FIRST_SVC" ]; then
                echo "# instances ($FIRST_SVC)"
                swctl --display yaml --base-url="$OAP_BASE_URL" instance list --service-name="$FIRST_SVC" 2>/dev/null || echo "ERROR"
                echo ""
                echo "# topology ($FIRST_SVC)"
                swctl --display yaml --base-url="$OAP_BASE_URL" dependency service --service-name="$FIRST_SVC" 2>/dev/null || echo "ERROR"
                echo ""
                echo "# service_cpm ($FIRST_SVC)"
                swctl --display yaml --base-url="$OAP_BASE_URL" metrics exec --expression=service_cpm --service-name="$FIRST_SVC" 2>/dev/null || echo "ERROR"
                echo ""
                echo "# service_resp_time ($FIRST_SVC)"
                swctl --display yaml --base-url="$OAP_BASE_URL" metrics exec --expression=service_resp_time --service-name="$FIRST_SVC" 2>/dev/null || echo "ERROR"
            fi
        } > "$out" 2>&1

        # Print summary to stdout
        local svc_count
        svc_count=$(grep -c "^- id:" "$out" 2>/dev/null || echo 0)
        local has_cpm
        has_cpm=$(grep -c 'value: "[1-9]' "$out" 2>/dev/null || echo 0)
        log "  metrics round $round: services=$svc_count, has_values=$has_cpm"

        sleep 10
    done
}
metrics_monitor &
BG_PIDS+=($!)

#############################################################################
# Wait for initial data
#############################################################################
log "--- Waiting 30s for initial data ---"
sleep 30

#############################################################################
# Thread dump collection
#############################################################################
log "--- Collecting $DUMP_COUNT thread dumps (${DUMP_INTERVAL}s apart) ---"

OAP_PODS=($(kubectl -n "$NAMESPACE" get pods -l "$OAP_SELECTOR" -o jsonpath='{.items[*].metadata.name}'))
log "OAP pods: ${OAP_PODS[*]}"

for i in $(seq 1 "$DUMP_COUNT"); do
    log "Thread dump round $i/$DUMP_COUNT..."
    for pod in "${OAP_PODS[@]}"; do
        safe_name="${pod//skywalking-oap-/oap-}"
        outfile="$REPORT_DIR/${safe_name}-dump-${i}.txt"
        echo "# Pod: $pod  Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$outfile"

        if kubectl -n "$NAMESPACE" exec "$pod" -c oap -- jstack 1 >> "$outfile" 2>&1; then
            log "  $pod: jstack OK ($(wc -l < "$outfile") lines)"
        elif kubectl -n "$NAMESPACE" exec "$pod" -c oap -- jcmd 1 Thread.print >> "$outfile" 2>&1; then
            log "  $pod: jcmd OK ($(wc -l < "$outfile") lines)"
        else
            # JRE-only images lack jstack/jcmd. Use kill -3 (SIGQUIT) which
            # triggers the JVM's built-in thread dump to stderr (container logs).
            log "  $pod: jstack/jcmd unavailable, using kill -3 (SIGQUIT)..."
            ts_before=$(date -u +%Y-%m-%dT%H:%M:%SZ)
            kubectl -n "$NAMESPACE" exec "$pod" -c oap -- kill -3 1 2>/dev/null || true
            sleep 3
            # Capture the thread dump from container logs since the signal
            kubectl -n "$NAMESPACE" logs "$pod" -c oap --since-time="$ts_before" >> "$outfile" 2>&1
            lines=$(wc -l < "$outfile")
            if [ "$lines" -gt 5 ]; then
                log "  $pod: kill -3 OK ($lines lines)"
            else
                log "  $pod: kill -3 produced only $lines lines (may have failed)"
            fi
        fi
    done
    if [ "$i" -lt "$DUMP_COUNT" ]; then
        log "  Sleeping ${DUMP_INTERVAL}s..."
        sleep "$DUMP_INTERVAL"
    fi
done

#############################################################################
# Thread dump analysis
#############################################################################
log "--- Analyzing thread dumps ---"

analyze_dumps() {
    local analysis="$REPORT_DIR/thread-analysis.txt"
    : > "$analysis"

    # JVM internal threads to exclude — not OAP application threads.
    local JVM_FILTER='(^C[12] CompilerThread|^Common-Cleaner$|^DestroyJavaVM$|^Finalizer$|^G1 |^GC Thread|^PIC-Cleaner$|^Reference Handler$|^Service Thread$|^Signal Dispatcher$|^Sweeper thread$|^VM |^startstop-support$)'

    # Normalize a pool name: strip numeric thread suffixes, replace embedded
    # IPs, hashcodes, and per-instance numbers with wildcards.
    normalize_pool() {
        sed -E 's/[-#][0-9]+$//' | sed -E 's/[-#][0-9]+$//' | sed -E 's/ [0-9]+$//' | \
            sed -E 's/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+_[0-9]+/*/g' | \
            sed -E 's/(HttpClient)-[0-9]+-(SelectorManager)/\1-*-\2/g' | \
            sed -E 's/(JdkHttpClientFactory)-[0-9]+-(pool)/\1-*-\2/g' | \
            sed -E 's/(CachedSingleThreadScheduler)-[0-9]+-(pool)/\1-*-\2/g' | \
            sed -E 's/^-?[0-9]{6,}-pool/scheduled-pool/g'
    }

    # Helper: extract and normalize pool names from a dump file (OAP threads only).
    extract_pools() {
        awk '/^"/ { s=$0; sub(/^"/, "", s); sub(/".*/, "", s); print s }' "$1" | \
            normalize_pool | grep -vE "$JVM_FILTER"
    }

    # Helper: count threads matching a pool name in a dump file.
    count_pool() {
        extract_pools "$1" | grep -cF -- "$2" || true
    }

    # Find all dump file prefixes (e.g., oap-0, oap-1)
    local prefixes=()
    for f in "$REPORT_DIR"/oap-*-dump-1.txt; do
        [ -f "$f" ] || continue
        local base
        base=$(basename "$f")
        prefixes+=("${base%-dump-1.txt}")
    done

    echo "================================================================" >> "$analysis"
    echo "  OAP Thread Analysis Report" >> "$analysis"
    echo "  Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$analysis"
    echo "  Dump rounds: $DUMP_COUNT x ${DUMP_INTERVAL}s apart" >> "$analysis"
    echo "  OAP pods: ${prefixes[*]}" >> "$analysis"
    echo "  Note: JVM internal threads (GC, compiler, VM) are excluded." >> "$analysis"
    echo "================================================================" >> "$analysis"
    echo "" >> "$analysis"

    # ── Per-pod sections ──────────────────────────────────────────────
    for pod_prefix in "${prefixes[@]}"; do
        echo "================================================================" >> "$analysis"
        echo "  $pod_prefix — Thread Count Trend" >> "$analysis"
        echo "================================================================" >> "$analysis"
        echo "" >> "$analysis"

        # Header
        printf "%-50s" "Pool Name" >> "$analysis"
        for d in $(seq 1 "$DUMP_COUNT"); do
            printf " %5s" "#$d" >> "$analysis"
        done
        printf "  %s\n" "States (latest)" >> "$analysis"
        printf "%-50s" "$(printf '%0.s-' {1..50})" >> "$analysis"
        for d in $(seq 1 "$DUMP_COUNT"); do
            printf " %5s" "-----" >> "$analysis"
        done
        printf "  %s\n" "---------------" >> "$analysis"

        # Collect all OAP pool names across all dumps for this pod
        local all_pools
        all_pools=$(mktemp)
        for d in $(seq 1 "$DUMP_COUNT"); do
            local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${d}.txt"
            [ -f "$dumpfile" ] || continue
            extract_pools "$dumpfile" | sort -u
        done | sort -u > "$all_pools"

        # Parse states from the latest dump for this pod
        local latest_dump="$REPORT_DIR/${pod_prefix}-dump-${DUMP_COUNT}.txt"
        local tmp_states
        tmp_states=$(mktemp)
        if [ -f "$latest_dump" ]; then
            awk '
                /^"/ {
                    tname = $0
                    sub(/^"/, "", tname)
                    sub(/".*/, "", tname)
                    state = "UNKNOWN"
                }
                /java\.lang\.Thread\.State:/ {
                    state = $0
                    sub(/.*State: /, "", state)
                    sub(/[^A-Z_].*/, "", state)
                    print tname "\t" state
                }
            ' "$latest_dump" | while IFS=$'\t' read -r tname tstate; do
                local pool
                pool=$(echo "$tname" | normalize_pool)
                echo "$pool	$tstate"
            done | grep -vE "$JVM_FILTER" | sort | awk -F'\t' '
            {
                pool = $1; state = $2
                states[pool, state]++
                if (!(pool in seen)) { seen[pool] = 1 }
            }
            END {
                for (key in states) {
                    split(key, parts, SUBSEP)
                    print parts[1] "\t" parts[2] "\t" states[key]
                }
            }' > "$tmp_states"
        fi

        local total_latest=0
        while IFS= read -r pool; do
            printf "%-50s" "$pool" >> "$analysis"
            local last_cnt=0
            for d in $(seq 1 "$DUMP_COUNT"); do
                local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${d}.txt"
                local cnt=0
                if [ -f "$dumpfile" ]; then
                    cnt=$(count_pool "$dumpfile" "$pool")
                fi
                printf " %5d" "$cnt" >> "$analysis"
                if [ "$d" -eq "$DUMP_COUNT" ]; then last_cnt=$cnt; fi
            done
            total_latest=$((total_latest + last_cnt))
            # Append states from latest dump
            local state_str=""
            if [ -f "$tmp_states" ]; then
                state_str=$(awk -F'\t' -v p="$pool" '$1 == p { printf "%s(%s) ", $2, $3 }' "$tmp_states")
            fi
            printf "  %s\n" "$state_str" >> "$analysis"
        done < "$all_pools"

        echo "" >> "$analysis"
        printf "%-50s" "TOTAL (OAP threads)" >> "$analysis"
        for d in $(seq 1 "$DUMP_COUNT"); do
            local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${d}.txt"
            local t=0
            if [ -f "$dumpfile" ]; then
                t=$(extract_pools "$dumpfile" | wc -l | tr -d ' ')
            fi
            printf " %5d" "$t" >> "$analysis"
        done
        echo "" >> "$analysis"
        echo "" >> "$analysis"

        rm -f "$all_pools" "$tmp_states"
    done

    # ── Pod Comparison (latest dump) ──────────────────────────────────
    if [ "${#prefixes[@]}" -ge 2 ]; then
        echo "================================================================" >> "$analysis"
        echo "  Pod Comparison (dump #$DUMP_COUNT)" >> "$analysis"
        echo "================================================================" >> "$analysis"
        echo "" >> "$analysis"

        # Collect all OAP pool names across both pods' latest dumps
        local cmp_pools
        cmp_pools=$(mktemp)
        for pod_prefix in "${prefixes[@]}"; do
            local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${DUMP_COUNT}.txt"
            [ -f "$dumpfile" ] || continue
            extract_pools "$dumpfile" | sort -u
        done | sort -u > "$cmp_pools"

        # Header
        printf "%-50s" "Pool Name" >> "$analysis"
        for pod_prefix in "${prefixes[@]}"; do
            printf " %8s" "$pod_prefix" >> "$analysis"
        done
        printf "  %s\n" "Diff" >> "$analysis"
        printf "%-50s" "$(printf '%0.s-' {1..50})" >> "$analysis"
        for pod_prefix in "${prefixes[@]}"; do
            printf " %8s" "--------" >> "$analysis"
        done
        printf "  %s\n" "----" >> "$analysis"

        local diff_count=0
        while IFS= read -r pool; do
            local counts=()
            for pod_prefix in "${prefixes[@]}"; do
                local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${DUMP_COUNT}.txt"
                local cnt=0
                if [ -f "$dumpfile" ]; then
                    cnt=$(count_pool "$dumpfile" "$pool")
                fi
                counts+=("$cnt")
            done

            # Check if counts differ across pods
            local has_diff=false
            for c in "${counts[@]}"; do
                if [ "$c" != "${counts[0]}" ]; then
                    has_diff=true
                    break
                fi
            done

            printf "%-50s" "$pool" >> "$analysis"
            for c in "${counts[@]}"; do
                printf " %8d" "$c" >> "$analysis"
            done
            if [ "$has_diff" = true ]; then
                printf "  %s\n" "<--" >> "$analysis"
                diff_count=$((diff_count + 1))
            else
                echo "" >> "$analysis"
            fi
        done < "$cmp_pools"

        echo "" >> "$analysis"
        # Totals
        printf "%-50s" "TOTAL (OAP threads)" >> "$analysis"
        for pod_prefix in "${prefixes[@]}"; do
            local dumpfile="$REPORT_DIR/${pod_prefix}-dump-${DUMP_COUNT}.txt"
            local t=0
            if [ -f "$dumpfile" ]; then
                t=$(extract_pools "$dumpfile" | wc -l | tr -d ' ')
            fi
            printf " %8d" "$t" >> "$analysis"
        done
        echo "" >> "$analysis"
        echo "" >> "$analysis"
        if [ "$diff_count" -gt 0 ]; then
            echo "$diff_count pool(s) differ between pods (marked with <--)." >> "$analysis"
        else
            echo "All OAP thread pools are identical across pods." >> "$analysis"
        fi
        echo "" >> "$analysis"

        rm -f "$cmp_pools"
    fi

    log "Analysis written to: $analysis"
}

analyze_dumps

#############################################################################
# Environment summary report
#############################################################################
log "--- Writing environment summary ---"

ENV_REPORT="$REPORT_DIR/environment.txt"
{
    echo "================================================================"
    echo "  Benchmark Report: Thread Analysis"
    echo "  Environment: $ENV_NAME"
    echo "  Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "================================================================"
    echo ""
    echo "--- Host ---"
    echo "  OS:       $(uname -s) $(uname -r)"
    echo "  Arch:     $(uname -m)"
    echo ""
    echo "--- Docker ---"
    echo "  Server:   $DOCKER_SERVER_VERSION"
    echo "  OS:       $DOCKER_OS"
    echo "  Driver:   $DOCKER_STORAGE_DRIVER"
    echo "  CPUs:     $DOCKER_CPUS"
    echo "  Memory:   ${DOCKER_MEM_GB} GB"
    echo ""
    echo "--- Tool Versions ---"
    echo "  kind:     $KIND_VERSION"
    echo "  kubectl:  ${KUBECTL_CLIENT_VERSION:-unknown}"
    echo "  Helm:     $HELM_VERSION"
    echo "  istioctl: ${ISTIOCTL_VERSION:-unknown}"
    echo "  swctl:    $(swctl --version 2>/dev/null | head -1 || echo unknown)"
    echo ""
    echo "--- OAP JRE ---"
    OAP_FIRST_POD=$(kubectl -n "$NAMESPACE" get pods -l "$OAP_SELECTOR" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$OAP_FIRST_POD" ]; then
        OAP_JAVA_VERSION=$(kubectl -n "$NAMESPACE" exec "$OAP_FIRST_POD" -c oap -- java -version 2>&1 || echo "unknown")
        OAP_OS_INFO=$(kubectl -n "$NAMESPACE" exec "$OAP_FIRST_POD" -c oap -- cat /etc/os-release 2>/dev/null \
            | grep -E '^(PRETTY_NAME|ID|VERSION_ID)=' | head -3 || echo "unknown")
        OAP_ARCH=$(kubectl -n "$NAMESPACE" exec "$OAP_FIRST_POD" -c oap -- uname -m 2>/dev/null || echo "unknown")
        echo "$OAP_JAVA_VERSION" | sed 's/^/  /'
        echo "  Arch:     $OAP_ARCH"
        echo "$OAP_OS_INFO" | sed 's/^/  /'
    else
        echo "  (could not query OAP pod)"
    fi
    echo ""
    echo "--- Kubernetes ---"
    echo "  Node image: K8s $K8S_NODE_MINOR (from kind.yaml)"
    echo "  Cluster:    $CLUSTER_NAME"
    echo "  Namespace:  $NAMESPACE"
    echo ""
    echo "--- K8s Node Resources ---"
    if [ -f "$REPORT_DIR/node-resources.txt" ]; then
        cat "$REPORT_DIR/node-resources.txt"
    else
        echo "  (not captured)"
    fi
    echo ""
    echo "--- Benchmark Config ---"
    echo "  OAP replicas:     2"
    echo "  Storage:          BanyanDB (standalone)"
    echo "  Istio:            ${ISTIO_VERSION:-N/A}"
    echo "  ALS analyzer:     ${ALS_ANALYZER:-N/A}"
    echo "  Traffic rate:     ~5 RPS"
    echo "  Thread dumps:     $DUMP_COUNT x ${DUMP_INTERVAL}s apart"
    echo ""
    echo "--- Pod Status (at completion) ---"
    kubectl -n "$NAMESPACE" get pods -o wide 2>/dev/null || echo "  (could not query)"
    echo ""
    echo "--- Bookinfo Pods (default namespace) ---"
    kubectl -n default get pods -o wide 2>/dev/null || echo "  (could not query)"
    echo ""
    echo "--- Pod Resource Usage (requests) ---"
    kubectl -n "$NAMESPACE" describe node 2>/dev/null \
        | sed -n '/Non-terminated Pods/,/Allocated resources/p' || echo "  (could not query)"
    echo ""
} > "$ENV_REPORT"

log "Environment summary: $ENV_REPORT"

#############################################################################
# Done
#############################################################################
log "=== Thread analysis complete ==="
log "Reports in: $REPORT_DIR"
log "  environment.txt      - Host, Docker, K8s resources, tool versions"
log "  node-resources.txt   - K8s node capacity and allocatable"
log "  thread-analysis.txt  - Thread pool summary and trends"
log "  metrics-round-*.yaml - Periodic swctl query results"
log "  oap-*-dump-*.txt    - Raw thread dumps"

# Stop background metrics monitor
cleanup_pids
BG_PIDS=()

log "Done. Environment is still running."
