## 10.5.0

#### Project
* Remove Groovy v1-v2 checker modules. Replace with v2-only DSL script execution tests in their
  respective analyzer modules (hierarchy, log-analyzer) and a separate `meter-analyzer-scripts-test`
  module for MAL tests requiring ProcessRegistry FQCN isolation. Delete `test/script-cases/` directory.
* Fix `MalRuleLoader.formatExp()` dropping method chain after the first dot in expressions with `expPrefix`.
* Fix checkstyle JMH exclusion patterns to match actual `jmh_generated` package name.

#### OAP Server
* Add the Zipkin Virtual GenAI e2e

#### UI

#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.5.0)

