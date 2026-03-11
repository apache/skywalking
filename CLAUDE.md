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
│   └── exporter/                  # Data export functionality
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

## Tips for AI Assistants

1. **Always check submodules**: Protocol changes may require submodule updates
2. **Generate sources first**: Run `mvnw compile` before analyzing generated code
3. **Install package**: Use `mvnw flatten:flatten install` to build the precompiler and export generated classes before running tests. ref to [compile skill doc](.claude/skills/compile/SKILL.md)
3. **Respect checkstyle**: No System.out, no @author, no Chinese characters
4. **Follow module patterns**: Use existing modules as templates
5. **Check multiple storage implementations**: Logic may vary by storage type
6. **OAL generates code**: Don't manually edit generated metrics classes
7. **Use Lombok**: Prefer annotations over boilerplate code
8. **Test both unit and integration**: Different test patterns for different scopes
9. **Documentation is rendered via markdown**: When reviewing docs, consider how they will be rendered by a markdown engine
10. **Relative paths in docs are valid**: Relative file paths (e.g., `../../../oap-server/...`) in documentation work both in the repo and on the documentation website, supported by website build tooling
