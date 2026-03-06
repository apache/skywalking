# Claude Code Skills for SkyWalking Development

[Claude Code](https://docs.anthropic.com/en/docs/claude-code) is Anthropic's CLI tool for AI-assisted coding.
This project provides a set of Claude Code skills (slash commands) that automate common development workflows.

**Note**: These skills are specific to Claude Code. They are defined in the `.claude/skills/` directory
and are not recognized by other AI coding tools.

## Available Skills

| Skill | Command | Description |
|-------|---------|-------------|
| Compile | `/compile [all\|backend\|javadoc\|checkstyle\|module-name]` | Build the OAP server, run javadoc checks, or verify checkstyle |
| Test | `/test [unit\|integration\|slow\|module-name]` | Run unit tests, integration tests, or slow integration tests matching CI |
| License | `/license [check\|fix\|deps]` | Check and fix Apache 2.0 license headers and dependency licenses |
| Pull Request | `/gh-pull-request` | Commit, push, and create a PR with pre-flight checks (compile, checkstyle, license headers) |
| E2E Debug | `/ci-e2e-debug <RUN_ID or URL>` | Download and inspect CI e2e test logs from GitHub Actions artifacts |
| Generate Classes | `/generate-classes <mal\|oal\|lal\|hierarchy\|all>` | Generate bytecode classes from DSL scripts for inspection |
| Run E2E | `/run-e2e [test-case-path]` | Run SkyWalking e2e tests locally |

## Typical Workflow

1. Make your code changes.
2. Run `/compile` to verify the build passes.
3. Run `/test` to run relevant tests.
4. Run `/license check` to verify license headers.
5. Run `/gh-pull-request` to commit, push, and open a PR.

If a CI e2e test fails after pushing, use `/ci-e2e-debug <RUN_ID>` to download and inspect the logs.
