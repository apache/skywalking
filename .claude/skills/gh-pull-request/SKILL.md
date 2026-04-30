---
name: gh-pull-request
description: Verify, commit, and push changes on a PR branch. Runs pre-flight checks (compile, checkstyle, license headers) before every push. Also creates the PR if one doesn't exist yet.
---

# PR Branch Workflow

Run pre-flight checks, commit, push, and optionally create a PR.

## Pre-flight checks

Run these checks before every commit+push and fix any failures:

### 1. Compile and checkstyle

```bash
# Checkstyle
./mvnw -B -q clean checkstyle:check

# Full build (compile + javadoc)
./mvnw clean flatten:flatten install javadoc:javadoc -B -q -Pall \
  -Dmaven.test.skip \
  -Dcheckstyle.skip \
  -Dgpg.skip
```

### 2. License header check

```bash
license-eye header check
```

If invalid files are found, fix with `license-eye header fix` and re-check.

### 3. Unnecessary fully-qualified class names

The project checkstyle forbids inline FQCNs — every type reference in code should resolve
through an `import`, not a fully-qualified name. Checkstyle does not always catch this (it
misses cases like inline `java.util.HashMap`, `java.util.concurrent.TimeUnit`, or
`org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics.Timer` used as a local
variable type, generic parameter, or `new` target). Audit the files the branch touched
before pushing:

Use the `Grep` tool (ripgrep) rather than BSD `grep` on macOS — the scan below relies on a
negative lookahead that BSD `grep` doesn't support and GNU `grep -P` does:

```
pattern: ^(?!\s*(import |package |\s*\*)).*\b(java\.util\.|java\.io\.|java\.nio\.|java\.util\.concurrent\.|javassist\.|org\.apache\.skywalking\.)[A-Z][A-Za-z0-9_]*
glob:    *.java
output_mode: content
-n: true
```

Scope the scan to files the branch touched, not the whole tree — pre-existing FQDNs on
unrelated files generate noise. Use `git diff --name-only master...HEAD -- '*.java'` to get
the changed list, then run the ripgrep pattern against each.

Acceptable exceptions (same as the `CLAUDE.md` rule):
 - Two classes with the same simple name would collide if both imported.
 - A Javadoc `{@link}` where the short name would be ambiguous to the reader.
 - Inside a string literal (e.g., a class name passed to `Class.forName`).

Fix every other hit — add an `import` and switch to the short name. This includes
`new java.util.HashMap<>()`, `java.util.Set<String>` parameter types, and
`org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics.Timer` as a local
variable type. Field declarations, method signatures, local variables, and generic
type arguments should all use the imported short name.

Re-run checkstyle after the fix — a sloppy `sed`/`replace_all` can corrupt the `import`
line itself (e.g., turning `import java.util.concurrent.locks.ReentrantLock;` into
`import ReentrantLock;`), which causes a cryptic checkstyle `Range [0, -1) out of
bounds for length N` error, not a normal violation line. If you see that error, inspect
the imports block first.

## Commit and push

After checks pass, commit and push:

```bash
git add <files>
git commit -m "<message>"
git push -u origin <branch-name>
```

### Branch strategy
- **Never work directly on master branch**
- If on master, create a new branch first: `git checkout -b feature/<name>` or `git checkout -b fix/<name>`

## Create PR (if not yet created)

Check whether a PR already exists for the current branch:

```bash
gh pr view --json number 2>/dev/null
```

If no PR exists, create one:

### PR title
Summarize the changes concisely. Examples:
- `Fix BanyanDB query timeout issue`
- `Add support for OpenTelemetry metrics`

### PR description

Read `.github/PULL_REQUEST_TEMPLATE` and use its **exact format with checkboxes**. Do NOT use a custom summary format.

Key template sections — uncomment the relevant one:

**For Bug Fixes:**
```
### Fix <bug description or issue link>
- [ ] Add a unit test to verify that the fix works.
- [ ] Explain briefly why the bug exists and how to fix it.
```

**For New Features:**
```
### <Feature description>
- [ ] If this is non-trivial feature, paste the links/URLs to the design doc.
- [ ] Update the documentation to include this new feature.
- [ ] Tests(including UT, IT, E2E) are added to verify the new feature.
- [ ] If it's UI related, attach the screenshots below.
```

**For Performance Improvements:**
```
### Improve the performance of <class or module or ...>
- [ ] Add a benchmark for the improvement.
- [ ] The benchmark result.
- [ ] Links/URLs to the theory proof or discussion articles/blogs.
```

**Always include:**
```
- [ ] If this pull request closes/resolves/fixes an existing issue, replace the issue number. Closes #<issue number>.
- [ ] Update the [`CHANGES` log](https://github.com/apache/skywalking/blob/master/docs/en/changes/changes.md).
```

### Create command

```bash
gh pr create --title "<title>" --body "$(cat <<'EOF'
<PR body from template>
EOF
)"
```

### Post-creation
- Add `copilot` as a reviewer: `gh pr edit <number> --add-reviewer copilot`
- Do NOT add AI assistant as co-author. Code responsibility is on the committer's hands.
- Return the PR URL when done.
