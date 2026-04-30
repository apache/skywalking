# CLAUDE.md - AI Assistant Guide for Apache SkyWalking

This file provides guidance for AI assistants working with the Apache SkyWalking codebase.

## Project Overview

Apache SkyWalking is an open-source APM (Application Performance Monitoring) system designed for microservices, cloud-native, and container-based architectures. It provides distributed tracing, service mesh telemetry analysis, metrics aggregation, alerting, and observability capabilities.

## Repository Structure

```
skywalking/
├── oap-server/                    # OAP (Observability Analysis Platform) backend server
│   ├── server-core/               # Core module with fundamental services
│   ├── server-library/            # Shared libraries (module system, util, etc.)
│   ├── server-receiver-plugin/    # Data receivers (gRPC, HTTP, Kafka, etc.)
│   ├── server-storage-plugin/     # Storage implementations (BanyanDB, Elasticsearch, etc.)
│   ├── server-cluster-plugin/     # Cluster coordination (Zookeeper, K8s, etc.)
│   ├── server-query-plugin/       # Query interfaces (GraphQL)
│   ├── server-alarm-plugin/       # Alerting system
│   ├── server-fetcher-plugin/     # Data fetchers
│   ├── server-configuration/      # Dynamic configuration providers
│   ├── oal-grammar/               # OAL (Observability Analysis Language) grammar
│   ├── oal-rt/                    # OAL runtime
│   ├── mqe-grammar/               # MQE (Metrics Query Engine) grammar
│   ├── mqe-rt/                    # MQE runtime
│   ├── server-testing/             # Shared test utilities (DSL test framework)
│   ├── analyzer/                  # Log and trace analyzers
│   ├── ai-pipeline/               # AI/ML pipeline components
│   ├── exporter/                  # Data export functionality
│   └── server-tools/              # Standalone tools (profile exporter) with mock providers
├── apm-protocol/                  # Protocol definitions (submodule)
│   └── apm-network/               # gRPC/Protobuf network protocols
├── skywalking-ui/                 # Web UI (submodule - skywalking-booster-ui)
├── apm-webapp/                    # Web application packaging
├── apm-dist/                      # Distribution packaging
├── docs/                          # Documentation
├── docker/                        # Docker build files
├── test/                          # E2E and integration tests
└── tools/                         # Development tools
```

## Architecture & Key Concepts

### Module System
SkyWalking uses a custom module/provider architecture based on Java SPI:

- **ModuleDefine**: Declares a module and its required services
- **ModuleProvider**: Implements a module with specific technology/approach
- **Service**: Interface that modules expose to other modules

Key pattern:
```java
public class XxxModule extends ModuleDefine {
    public Class[] services() {
        return new Class[] { XxxService.class };
    }
}

public class XxxModuleProvider extends ModuleProvider {
    public void prepare() { /* initialize */ }
    public void start() { /* start services */ }
}
```

### Core Concepts
- **OAL (Observability Analysis Language)**: DSL for defining metrics aggregation rules
- **MQE (Metrics Query Engine)**: DSL for querying metrics
- **LAL (Log Analysis Language)**: DSL for log processing
- **MAL (Meter Analysis Language)**: DSL for meter data processing
- **Source/Scope**: Data model definitions for telemetry data
- **Stream Processing**: Metrics, Records, and TopN processing pipelines

### Data Flow
1. Agents/Collectors send data via gRPC/HTTP/Kafka
2. Receiver plugins parse and validate data
3. Analysis engine processes data using OAL/LAL/MAL
4. Storage plugins persist aggregated data
5. Query plugins serve data to UI/API

## Code Style & Conventions

### Checkstyle Rules (enforced via `apm-checkstyle/checkStyle.xml`)

**Prohibited patterns:**
- No `System.out.println` - use proper logging (SLF4J)
- No `@author` tags - ASF projects don't use author annotations
- No Chinese characters in source files
- No tab characters (use 4 spaces)
- No star imports (`import xxx.*`)
- No unused or redundant imports
- No empty statements (standalone `;`)
- No fully-qualified class names inline in code — always add an `import` statement and
  use the short name. Acceptable exceptions: (a) two classes with the same simple name
  would collide if both imported, (b) the class appears exactly once in a Javadoc
  `{@link}` where the short name would be ambiguous to the reader. Field declarations,
  method signatures, local variables, and generic type arguments should always use the
  imported short name — `private RemoteClientManager rcm;`, not `private
  org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager rcm;`.
- No one-line delegate methods. A wrapper whose only body is a single forwarding call
  to another class (`return Other.foo(a, b);`) adds a hop without value. Inline the
  call at the use site, or call the underlying object directly (including via method
  reference: `obj::foo` instead of `this::wrapperOfFoo`).

**Required patterns:**
- `@Override` annotation required for overridden methods
- `equals()` and `hashCode()` must be overridden together
- Javadoc `@param`, `@return`, `@throws` must have descriptions
- Long constants must use uppercase `L` (e.g., `100L` not `100l`)
- `default` case must come last in switch statements
- One statement per line

**Naming conventions:**
- Constants/static variables: `UPPER_CASE_WITH_UNDERSCORES`
- Type parameters: `UPPER_CASE` (e.g., `TYPE`, `KEY`, `VALUE`)
- Package names: `org.apache.skywalking.*` or `test.apache.skywalking.*`
- Type names: `PascalCase` or `UPPER_CASE_WITH_UNDERSCORES`
- Local variables/parameters/members: `camelCase`
- **Function-oriented naming, not abstract metaphor**: classes and methods are named for
  what they do, not for an abstract concept. Prefer concrete verbs (`load`, `apply`,
  `unregister`, `compile`, `verify`, `commit`, `rollback`) over metaphorical ones
  (`seed`, `hydrate`, `bootstrap`, `prime`). Class names follow the same rule —
  `StaticRuleLoader` (loads static rules), not `StaticBundleSeeder`; `DSLSyncTimer` (syncs
  DB → state on a timer), not `TickRunner`. If you can't name a method without reaching
  for a metaphor, the method is probably doing too much; split it.

**File limits:**
- Max file length: 3000 lines

**Whitespace:**
- Whitespace required after commas, semicolons, type casts
- Whitespace required around operators
- No multiple consecutive blank lines
- Empty line separators between class members (fields can be grouped)

### Code Style (via `codeStyle.xml` for IntelliJ IDEA)

**Indentation:**
- 4-space indentation
- 4-space continuation indent

**Imports:**
- No star imports (threshold set to 999)
- Import order: regular imports, blank line, static imports

**Formatting:**
- `while` in do-while on new line
- Align multiline chained method calls
- Align multiline parameters in calls
- Array initializer braces on new lines
- Wrap long method call chains

**General:**
- Use `final` for local variables and parameters
- Use Lombok annotations (`@Getter`, `@Setter`, `@Builder`, `@Data`, `@Slf4j`, etc.)
- Follow existing patterns in similar files

### License Header
Java, XML, and YAML/YML files must include the Apache 2.0 license header (see `HEADER` file).
JSON and Markdown files are excluded (JSON doesn't support comments, see `.licenserc.yaml`).

### JDK 11 Compatibility

All code must be compatible with JDK 11 (LTS). The project supports JDK 11, 17, and 21.

**Prohibited Java features (post-JDK 11):**

| Feature | JDK Version | Use Instead |
|---------|-------------|-------------|
| Switch expressions (`->`) | 14+ | Traditional `switch` with `case:` and `break` |
| `Stream.toList()` | 16+ | `.collect(Collectors.toList())` |
| Text blocks (`"""..."""`) | 15+ | String concatenation or `+` |
| Records | 14+ | Regular classes with Lombok `@Data` |
| Pattern matching for `instanceof` | 14+ | Traditional cast after `instanceof` |
| Sealed classes/interfaces | 15+ | Regular classes/interfaces |

**Allowed Java features (JDK 11 compatible):**
- `List.of()`, `Set.of()`, `Map.of()` - Immutable collections (Java 9+)
- `Optional` methods - `orElseThrow()`, `ifPresentOrElse()` (Java 9+)
- Lambda expressions and method references (Java 8+)
- Stream API (Java 8+)
- Lombok annotations (`@Getter`, `@Builder`, `@Data`, `@Slf4j`)

**Verification commands:**
```bash
# Check for switch expressions (should return no matches)
grep -r "switch.*->" src/ --include="*.java"

# Check for Stream.toList() (should return no matches)
grep -r "\.toList()" src/ --include="*.java"

# Check for text blocks (should return no matches)
grep -r '"""' src/ --include="*.java"
```

## Git Submodules

The project uses submodules for protocol definitions and UI:
- `apm-protocol/apm-network/src/main/proto` - skywalking-data-collect-protocol
- `oap-server/server-query-plugin/.../query-protocol` - skywalking-query-protocol
- `skywalking-ui` - skywalking-booster-ui
- `oap-server/server-library/library-banyandb-client/src/main/proto` - banyandb-client-proto

Always use `--recurse-submodules` when cloning or update submodules manually.

## Key Files for Understanding the Codebase

- `oap-server/server-core/src/main/java/.../CoreModule.java` - Core module definition
- `oap-server/server-library/library-module/src/main/java/.../ModuleDefine.java` - Module system base
- `oap-server/oal-grammar/src/main/antlr4/.../OALParser.g4` - OAL grammar definition
- `oap-server/server-starter/` - Application entry point
- `docs/en/concepts-and-designs/` - Architecture documentation

## Common Development Tasks

### Adding a New Receiver Plugin
1. Create module in `server-receiver-plugin/`
2. Implement `ModuleDefine` and `ModuleProvider`
3. Register via SPI in `META-INF/services/`
4. Add configuration to `application.yml`

### Adding a New Storage Plugin
1. Create module in `server-storage-plugin/`
2. Implement storage DAOs for each data type
3. Follow existing plugin patterns (e.g., BanyanDB, elasticsearch)

### Modifying OAL Metrics
1. Edit `.oal` files in `oap-server/server-starter/src/main/resources/oal/`
2. Regenerate by building the project
3. Update storage schema if needed

### MAL Scripts (in `oap-server/server-starter/src/main/resources/`)
- `otel-rules/` - OpenTelemetry metrics (Prometheus, etc.)
- `meter-analyzer-config/` - SkyWalking native meter protocol

### LAL Scripts (in `oap-server/server-starter/src/main/resources/`)
- `lal/` - Log processing rules
- `log-mal-rules/` - Metrics extracted from logs

## Documentation (in `docs/en/`, structure defined in `docs/menu.yml`)

- `concepts-and-designs/` - Architecture and core concepts (OAL, MAL, LAL, profiling)
- `setup/` - Installation and configuration guides
- `api/` - Telemetry and query protocol documentation
- `guides/` - Contributing guides, build instructions, testing
- `changes/changes.md` - Changelog (update when making changes)
- `swip/` - SkyWalking Improvement Proposals

## Submitting Pull Requests

Use the `/gh-pull-request` skill for committing and pushing to a PR branch. It runs pre-flight checks (compile, checkstyle, license headers) before every push, and creates the PR if one doesn't exist yet.

## GitHub Actions Allow List

Apache enforces an allow list for third-party GitHub Actions. All third-party actions must be pinned to an approved SHA from:
https://github.com/apache/infrastructure-actions/blob/main/approved_patterns.yml

If a PR is blocked by "action is not allowed" errors, check the approved list and update `.github/workflows/` files to use the approved SHA pin instead of a version tag.

Actions owned by `actions/*` (GitHub), `github/*`, and `apache/*` are always allowed (enterprise-owned).

## Tips for AI Assistants

1. **Always check submodules**: Protocol changes may require submodule updates
2. **Generate sources first**: Run `mvnw compile` before analyzing generated code
3. **Install package**: Use `mvnw flatten:flatten install` to build the precompiler and export generated classes before running tests. ref to [compile skill doc](.claude/skills/compile/SKILL.md)
3. **Full rebuild on cross-module changes**: If you changed more than two modules or pulled/rebased code from git remote, run `mvnw clean install` (or `mvnw clean package`) on the **whole project** rather than picking individual modules with `-pl`. Incremental `-pl ... -am` builds can leave stale jars in `.m2` or `oap-libs/` when jar sizes don't change but content does, causing hard-to-debug runtime issues.
3. **Respect checkstyle**: No System.out, no @author, no Chinese characters
4. **Follow module patterns**: Use existing modules as templates
5. **Check multiple storage implementations**: Logic may vary by storage type
6. **OAL generates code**: Don't manually edit generated metrics classes
7. **Use Lombok**: Prefer annotations over boilerplate code
8. **Test both unit and integration**: Different test patterns for different scopes
9. **Documentation is rendered via markdown**: When reviewing docs, consider how they will be rendered by a markdown engine
10. **Relative paths in docs are valid**: Relative file paths (e.g., `../../../oap-server/...`) in documentation work both in the repo and on the documentation website, supported by website build tooling
11. **Module service registration**: When adding a service to `CoreModule.services()`, update ALL `CoreModuleProvider` implementations — not just the main one. Search with `grep -rn "extends CoreModuleProvider" oap-server/ --include="*.java"`. The `MockCoreModuleProvider` in `server-tools/profile-exporter/` also needs it, or the profile exporter e2e test will fail at startup.
12. **Multiple OAP packagings**: The OAP server is not only the main `server-starter`. The `server-tools/` directory contains standalone tools (e.g., profile exporter) that boot with mock module providers and a subset of modules. Changes to core module contracts (services, required modules) must be reflected in these tools too.
13. **`moduleManager.find(X.NAME)` requires `X.NAME` in `requiredModules()`**: every call to `moduleManager.find(SomeModule.NAME)` (direct or through a helper) must have `SomeModule.NAME` in the provider's `requiredModules()` array. Missing declarations cause runtime exceptions the first time the code path fires — not at module boot. Wrapping the call in `try { ... } catch (Throwable)` is NOT a substitute; declare the module and keep the try/catch only for defensive handling of transient provider outages. When auditing a branch, grep for `moduleManager.find(` across the touched module and verify each target name appears in `requiredModules()`. Example modules that frequently catch teams out: `AlarmModule` (used by alarm-kernel reset), `LogAnalyzerModule` (used by LAL factory lookup).
14. **Don't look up `ClusterModule` services directly**: the `ClusterModule` (ZooKeeper / K8s / Nacos coordination) exposes `ClusterRegister` / `ClusterNodesQuery` / `ClusterCoordinator`. Most receiver / analyzer modules don't declare `ClusterModule` in `requiredModules()`, so calling `moduleManager.find(ClusterModule.NAME)` will throw at runtime. Instead, go through `CoreModule`'s `RemoteClientManager` service — it's already populated by the cluster module and exposes the peer list every OAP needs. If a module genuinely needs cluster-coordinator primitives, declare `ClusterModule.NAME` in `requiredModules()` explicitly.
15. **No `ThreadLocal` side-channels to hijack downstream behaviour**: routing a caller's intent through a `ThreadLocal` that downstream code reads (e.g., `if (PeerMode.isActive()) skipSomething()`) is almost always the wrong answer — it creates invisible coupling between far-apart code paths, leaks across async hand-offs (executors, gRPC threads, Armeria event loops), and makes the behaviour impossible to understand from a method signature. The correct fix is almost always to **extend the interface** — add a parameter, a new method, a new mode enum that appears in the signature. Rare exceptions: propagating OpenTelemetry context where the whole industry has standardised on `ThreadLocal`, or security principals enforced by a framework. In all other cases, prefer an explicit API extension, even if it costs more lines.
16. **BanyanDB schema-visibility: fence on `mod_revision`, do NOT poll metadata**: every BanyanDB Create / Update / Delete returns an etcd `mod_revision` (0 on a delete that didn't record a tombstone). After firing DDL, fence on `BanyanDBClient.getSchemaWatcher().awaitRevisionApplied(maxRev, timeout)` before unparking dispatch / firing data writes — this blocks until every data node has caught up, which the registry's read-after-write does not guarantee. For deletes that returned `mod_revision == 0`, fall back to `awaitSchemaDeleted(SchemaKey, timeout)`. The previous "poll `findMeasure` until you can read your own write" idiom existed before the `SchemaBarrierService` proto landed and has been replaced — do not reintroduce it. JDBC and ES are synchronous-DDL on the coordinator so they don't need a fence.

## Analysis and Design Principles

**Never guess or speculate.** All analysis must be grounded in source code, documentation, or verified behavior.

### Before making claims
- **Read the source code** — don't assume how a feature works based on naming or convention. Check the actual implementation.
- **Read the documentation** — check `docs/en/`, CLAUDE.md files in submodules, and README files before proposing designs.
- **Check configuration and flags** — verify what flags/env vars exist, their default values, and how they are parsed (e.g., BanyanDB uses viper with `BYDB_` prefix to auto-bind flags to env vars).
- **Check dependent projects** — SkyWalking depends on BanyanDB, infra-e2e, Helm charts, etc. Read their source code and docs before assuming capabilities (e.g., check Helm chart `values.yaml` for supported fields, check infra-e2e for supported config options). For `skywalking-*` projects, ask the developer if they have the source code locally — searching a local clone is much faster than fetching files via GitHub API.

### Before proposing changes
- **Verify locally first** — run the code, start the container, execute the test before pushing to CI. Don't use CI as a trial-and-error environment.
- **Validate file paths and directory structures** — check where data actually goes (e.g., BanyanDB `--stream-root-path /tmp` creates `/tmp/stream/`, `--access-log-root-path /tmp` creates `/tmp/accesslog/`). Don't assume directory names.
- **Validate YAML syntax** — after editing YAML files (especially with sed/awk), validate with a YAML parser before committing. Corrupted YAML causes silent failures in CI.
- **Check the actual Docker image** — verify what's available in the container (binaries, shell, directories) before writing commands that depend on them.

### When uncertain
- **Say "I don't know" and investigate** — reading the code is always better than guessing. Use grep, find, and read tools to locate the answer.
- **Ask the developer first** — if you can't find the source code, don't know how to run something, or the code doesn't make the answer clear, ask the developer where to find it rather than speculate.
- **Test with real data** — when investigating runtime behavior (e.g., what model names an API returns, what directory structure BanyanDB creates), set up a local test and observe the actual output.

### Docker images
- **Apache SkyWalking projects** — images are on `ghcr.io/apache/` (e.g., `ghcr.io/apache/skywalking-banyandb:${COMMIT_SHA}`). Tags are full commit SHAs, not short SHAs or version tags.
- **Official and 3rd-party images** — on Docker Hub (e.g., `ollama/ollama`, `otel/opentelemetry-collector`, `envoyproxy/gateway`).
- **Always verify the image exists** — `docker pull` before writing CI or e2e configs. Image tags depend on CI publish workflows completing successfully.
