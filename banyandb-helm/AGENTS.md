<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License. -->

# AI Assistant Rules for the SkyWalking BanyanDB Helm Chart

## Chart principles

1. **`image.tag` never has a default.** `chart/values.yaml` and
   `chart/values-lifecycle.yaml` keep `image.tag: ""` on purpose: every
   install must pick an explicit tag (`--set image.tag=...` / `--values`),
   so nobody runs a stale pinned sha by accident. Templates enforce it with
   `required "banyandb.image.tag is required"`. Never commit a concrete tag
   into a values file — test/e2e values files are the only exception (they
   pin a known-good sha deliberately).

## Conventions

- Values files document every key with `## @param` comments; keep
  `doc/parameters.md` in sync (bitnami readme-generator format).
- Any user-facing change needs a `CHANGES.md` entry under the current version.
- New components ship with an e2e scenario in `test/e2e/` wired into the
  `e2e.ci.yaml` matrix, verified locally on kind before opening the PR.
- Workflow action references must match the ASF allowlist
  (apache/infrastructure-actions `approved_patterns.yml`); prefer SHA pins.
