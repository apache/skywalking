---
name: gh-pull-request
description: Create a GitHub pull request for the current branch. Runs pre-flight checks (compile, checkstyle, license headers), commits, pushes, and creates the PR using the project PR template.
argument-hint: "[issue-number]"
---

# Create Pull Request

Create a GitHub pull request for the current branch with pre-flight validation.

## Pre-flight checks

Before committing and pushing, run these checks and fix any failures:

### 1. Compile and checkstyle

```bash
# Checkstyle
./mvnw -B -q clean checkstyle:check

# Full build (compile + javadoc)
./mvnw clean install javadoc:javadoc -B -q -Pall \
  -Dmaven.test.skip \
  -Dcheckstyle.skip \
  -Dgpg.skip
```

### 2. License header check

```bash
license-eye header check
```

If invalid files are found, fix with `license-eye header fix` and re-check.

## Create the PR

### Branch strategy
- **Never work directly on master branch**
- If on master, create a new branch first: `git checkout -b feature/<name>` or `git checkout -b fix/<name>`

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

### Push and create PR

```bash
git push -u origin <branch-name>
gh pr create --title "<title>" --body "$(cat <<'EOF'
<PR body from template>
EOF
)"
```

### Post-creation
- Add `copilot` as a reviewer: `gh pr edit <number> --add-reviewer copilot`
- Do NOT add AI assistant as co-author. Code responsibility is on the committer's hands.
- Return the PR URL when done.
