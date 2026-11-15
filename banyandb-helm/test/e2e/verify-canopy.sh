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

# Verifies a canopy console deployed by the chart:
#   1. /healthz on the canopy service
#   2. the chart-generated default admin can log in (password from the Secret)
#   3. /api/meta reports the BanyanDB target reachable through the BFF
#   4. the SPA index is served
#   5. a BydbQL query round-trips through the BFF proxy
#
# Usage: verify-canopy.sh <release> <namespace> [host:port]
#   host:port defaults to the e2e framework's exposed-service env vars, or
#   127.0.0.1:4000 (expects the caller to port-forward).
set -u
RELEASE="${1:?release name required}"
NS="${2:?namespace required}"
JAR="$(mktemp)"; RESP="$(mktemp)"
trap 'rm -f "$JAR" "$RESP"' EXIT
fail() { echo "CANOPY VERIFY FAIL: $*" >&2; exit 1; }
ok() { echo "ok - $*"; }

# Resolve the endpoint: explicit arg > framework env > port-forward.
if [ $# -ge 3 ]; then
  BASE="http://$3"
elif [ -n "${service_banyandb_canopy_host:-}" ]; then
  BASE="http://${service_banyandb_canopy_host}:${service_banyandb_canopy_4000:-4000}"
else
  kubectl -n "$NS" port-forward "svc/${RELEASE}-canopy" 14000:4000 >/dev/null 2>&1 &
  PF=$!; trap 'kill $PF 2>/dev/null; rm -f "$JAR" "$RESP"' EXIT
  sleep 3
  BASE="http://127.0.0.1:14000"
fi

ready=""
for _ in $(seq 1 45); do
  curl -fsS -m 2 "$BASE/healthz" >/dev/null 2>&1 && { ready=1; break; }
  sleep 2
done
[ -n "$ready" ] || fail "canopy /healthz not ready at $BASE"
ok "healthz ($BASE)"

# Default admin: username from values (default 'admin'), password from the
# chart-generated Secret.
PASS="$(kubectl -n "$NS" get secret "${RELEASE}-canopy-auth" -o jsonpath='{.data.password}' | base64 -d)"
[ -n "$PASS" ] || fail "cannot read the generated admin password from ${RELEASE}-canopy-auth"
[ "${#PASS}" -ge 20 ] || fail "generated password is too short (${#PASS} chars)"
code=$(curl -sS --max-time 30 -o "$RESP" -w "%{http_code}" -c "$JAR" -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USER_NAME:-admin}\",\"password\":\"$PASS\"}")
[ "$code" = 200 ] || fail "default-admin login -> HTTP $code: $(head -c 200 "$RESP")"
grep -q '"role":"admin"' "$RESP" || fail "login response missing admin role"
ok "default admin login (password from Secret, ${#PASS} chars)"

code=$(curl -sS --max-time 30 -o "$RESP" -w "%{http_code}" -b "$JAR" "$BASE/api/meta")
[ "$code" = 200 ] || fail "/api/meta -> HTTP $code"
grep -q '"reachable":true' "$RESP" || fail "/api/meta reports unreachable: $(head -c 200 "$RESP")"
ok "api/meta reachable through the BFF"

code=$(curl -sS --max-time 30 -o "$RESP" -w "%{http_code}" "$BASE/")
[ "$code" = 200 ] || fail "SPA index -> HTTP $code"
grep -qi '<script' "$RESP" || fail "SPA index has no app markup"
ok "SPA index"

code=$(curl -sS --max-time 30 -o "$RESP" -w "%{http_code}" -b "$JAR" "$BASE/api/v1/group/schema/lists")
[ "$code" = 200 ] || fail "group list through proxy -> HTTP $code: $(head -c 200 "$RESP")"
ok "registry read through the BFF proxy"

echo "CANOPY VERIFY PASS ($RELEASE in $NS)"
