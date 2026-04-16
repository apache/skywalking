---
name: ci-e2e-debug
description: Download and inspect CI e2e test logs from GitHub Actions artifacts. Use when investigating e2e test failures in CI.
argument-hint: "<RUN_ID or GitHub Actions URL>"
---

# CI E2E Debug Skill

Download test log artifacts from a GitHub Actions CI run and inspect them for errors.

## Steps

1. **Find the artifact**: Use `gh api` to list artifacts for the given CI run:
   ```bash
   gh api repos/apache/skywalking/actions/runs/<RUN_ID>/artifacts --jq '.artifacts[] | {id: .id, name: .name}'
   ```

2. **Download and extract**: Download the artifact zip and extract it:
   ```bash
   cd /tmp && rm -rf e2e-debug-logs && mkdir e2e-debug-logs && cd e2e-debug-logs
   gh api repos/apache/skywalking/actions/artifacts/<ARTIFACT_ID>/zip > artifact.zip
   unzip -o artifact.zip
   ```

3. **Inspect OAP logs**: Look for errors in the OAP server logs:
   ```bash
   # Find OAP log files
   find /tmp/e2e-debug-logs -name "skywalking-oap-*.log" -o -name "oap.log"
   # Check for errors
   grep -E "ERROR|Exception|FATAL|CannotCompileException" <log_file> | head -30
   ```

4. **Inspect other component logs**: Check BanyanDB, UI, and other pod logs as needed.

5. **Report findings**: Summarize the root cause error from the logs.

## Notes
- CI artifacts are automatically uploaded by the e2e test framework to `$SW_INFRA_E2E_LOG_DIR`
- Log files are organized by namespace/pod name
- OAP init pods may have different errors than the main OAP pod — check all of them
- Common errors: MAL/LAL/OAL compilation failures, storage connection issues, module initialization errors
- **Profile exporter tests** (e.g., Trace Profiling ES): the exporter runs as a separate process with `MockCoreModuleProvider`. If a new service is added to `CoreModule.services()` but not registered in the mock, the exporter fails at startup with `requiredCheck()` error — but the OAP logs will show no errors (OAP is fine, the exporter subprocess is what fails). Check the test step that runs `profile_exporter.sh`.
- **When OAP logs are clean but tests fail**: look at which specific test step failed. Some e2e tests invoke standalone tools (profile exporter, data generator) that boot with mock providers and may fail independently of OAP.
