---
name: license
description: Check and fix Apache 2.0 license headers and dependency licenses using skywalking-eyes. Use before submitting a PR.
argument-hint: "[check|fix|deps]"
---

# License Checks

Check and fix license compliance using [skywalking-eyes](https://github.com/apache/skywalking-eyes). Two CI jobs use this tool:

1. **license-header** — verifies all source files have Apache 2.0 headers
2. **dependency-license** — verifies the LICENSE file matches current dependencies

## Steps

### Header check (default, or `check` argument)

```bash
license-eye header check
```

- **All valid**: Output shows `valid: N, invalid: 0` — nothing to do.
- **Invalid files found**: Fix with `license-eye header fix`, then re-check.

### Header fix (`fix` argument)

```bash
license-eye header fix
license-eye header check
```

### Dependency license check (`deps` argument)

This regenerates the LICENSE file from dependency metadata and checks for drift:

```bash
license-eye dependency resolve --summary ./dist-material/release-docs/LICENSE.tpl || exit 1
if [ ! -z "$(git diff -U0 ./dist-material/release-docs/LICENSE)" ]; then
  echo "LICENSE file is not updated correctly"
  git diff -U0 ./dist-material/release-docs/LICENSE
fi
```

If the LICENSE file changed, review the diff and commit it. **Important**: CI runs on Linux — some dependencies have platform-specific variants. If you're on macOS/Windows, the LICENSE diff may be a platform artifact. Verify before committing.

## Rules

Configuration is in `.licenserc.yaml`:
- Java, XML, YAML/YML files require Apache 2.0 headers
- JSON and Markdown files are excluded (JSON doesn't support comments)
- Generated files and certain vendor paths are excluded
- SPI service files (`META-INF/services/`) require headers (use `#` comment style)

## Installation

```bash
# Same version as CI (pinned commit)
go install github.com/apache/skywalking-eyes/cmd/license-eye@5b7ee1731d036b5aac68f8bd3fc9e6f98ada082e

# Or via Homebrew (macOS)
brew install license-eye
```
