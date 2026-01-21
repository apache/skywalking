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

## Build System

### Prerequisites
- JDK 11, 17, or 21 (LTS versions)
- Maven 3.6+
- Git (with submodule support)

### Common Build Commands

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/apache/skywalking.git

# Or initialize submodules after clone
git submodule init && git submodule update

# Full build (skip tests)
./mvnw clean package -Dmaven.test.skip

# Build backend only
./mvnw package -Pbackend,dist
# or: make build.backend

# Build UI only
./mvnw package -Pui,dist
# or: make build.ui

# Run tests
./mvnw test

# Run integration tests
./mvnw integration-test

# Build with all profiles
./mvnw clean package -Pall -Dmaven.test.skip
```

### Maven Profiles
- `backend` (default): Builds OAP server modules
- `ui` (default): Builds web application
- `dist` (default): Creates distribution packages
- `all`: Builds everything including submodule initialization

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

## Testing

### Test Frameworks
- JUnit 5 (`org.junit.jupiter`)
- Mockito for mocking
- AssertJ for assertions
- PowerMock for reflection utilities

### Test Naming
- Unit tests: `*Test.java`
- Integration tests: `IT*.java` or `*IT.java`

### Running Tests
```bash
# Unit tests only
./mvnw test

# Integration tests
./mvnw integration-test

# Skip tests during build
./mvnw package -Dmaven.test.skip
```

## E2E Testing

SkyWalking uses [Apache SkyWalking Infra E2E](https://github.com/apache/skywalking-infra-e2e) for end-to-end testing. E2E tests validate the entire system including OAP server, storage backends, agents, and integrations.

### E2E Tool Installation

```bash
# Install the same version used in CI (recommended)
go install github.com/apache/skywalking-infra-e2e/cmd/e2e@e7138da4f9b7a25a169c9f8d995795d4d2e34bde

# Verify installation
e2e --help
```

### E2E Test Structure

```
test/e2e-v2/
├── cases/                    # 50+ test case directories
│   ├── simple/jdk/           # Basic Java agent test
│   ├── storage/              # Storage backend tests (BanyanDB, ES, MySQL, PostgreSQL)
│   ├── alarm/                # Alerting tests
│   ├── profiling/            # Profiling tests (trace, eBPF, async)
│   ├── kafka/                # Kafka integration
│   ├── istio/                # Service mesh tests
│   └── ...
├── script/
│   ├── env                   # Environment variables (agent commits, versions)
│   ├── docker-compose/
│   │   └── base-compose.yml  # Base service definitions (oap, banyandb, provider, consumer)
│   └── prepare/
│       └── setup-e2e-shell/  # Tool installers (swctl, yq, kubectl, helm)
└── java-test-service/        # Test service implementations
    ├── e2e-service-provider/
    ├── e2e-service-consumer/
    └── ...
```

### E2E Configuration (e2e.yaml)

Each test case has an `e2e.yaml` with four sections:

```yaml
setup:
  env: compose                              # Environment: compose or kind (Kubernetes)
  file: docker-compose.yml                  # Docker compose file
  timeout: 20m                              # Setup timeout
  init-system-environment: ../../../script/env  # Shared env variables
  steps:                                    # Initialization steps
    - name: install swctl
      command: bash test/e2e-v2/script/prepare/setup-e2e-shell/install.sh swctl

trigger:
  action: http                              # Generate test traffic
  interval: 3s
  times: -1                                 # -1 = run until verify succeeds
  url: http://${consumer_host}:${consumer_9092}/users
  method: POST
  body: '{"id":"123","name":"skywalking"}'

verify:
  retry:
    count: 20
    interval: 10s
  cases:
    - includes:
        - ../simple-cases.yaml              # Reusable verification cases
    - query: swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql metrics exec ...
      expected: expected/metrics.yml

cleanup:
  on: always                                # always|success|failure|never
```

### Running E2E Tests Locally

**Prerequisites:**
- Docker and Docker Compose
- Go (for e2e tool installation)

**Quick Start (run simple/jdk test):**
```bash
# 1. Build distribution and Docker image
./mvnw clean package -Pall -Dmaven.test.skip
make docker

# 2. Build test services
./mvnw -f test/e2e-v2/java-test-service/pom.xml clean package

# 3. Run e2e test (SW_AGENT_JDK_VERSION is required)
SW_AGENT_JDK_VERSION=8 e2e run -c test/e2e-v2/cases/simple/jdk/e2e.yaml
```

**Step-by-step debugging:**
```bash
# Set required environment variable
export SW_AGENT_JDK_VERSION=8

# Run individual steps instead of full test
e2e setup -c test/e2e-v2/cases/simple/jdk/e2e.yaml    # Start containers
e2e trigger -c test/e2e-v2/cases/simple/jdk/e2e.yaml  # Generate traffic
e2e verify -c test/e2e-v2/cases/simple/jdk/e2e.yaml   # Validate results
e2e cleanup -c test/e2e-v2/cases/simple/jdk/e2e.yaml  # Stop containers
```

### E2E CLI Commands

| Command | Description |
|---------|-------------|
| `e2e run -c <path>` | Run complete test (setup → trigger → verify → cleanup) |
| `e2e setup -c <path>` | Start containers and initialize environment |
| `e2e trigger -c <path>` | Generate test traffic |
| `e2e verify -c <path>` | Validate results against expected output |
| `e2e cleanup -c <path>` | Stop and remove containers |

### Common Test Cases

| Category | Path | Description |
|----------|------|-------------|
| `simple/jdk` | `test/e2e-v2/cases/simple/jdk/` | Basic Java agent with BanyanDB |
| `storage/banyandb` | `test/e2e-v2/cases/storage/banyandb/` | BanyanDB storage backend |
| `storage/elasticsearch` | `test/e2e-v2/cases/storage/elasticsearch/` | Elasticsearch storage |
| `alarm/` | `test/e2e-v2/cases/alarm/` | Alerting functionality |
| `profiling/trace` | `test/e2e-v2/cases/profiling/trace/` | Trace profiling |
| `log/` | `test/e2e-v2/cases/log/` | Log analysis (LAL) |

### Writing E2E Tests

1. **Create test directory** under `test/e2e-v2/cases/<category>/<name>/`

2. **Create docker-compose.yml** extending base services:
   ```yaml
   version: '2.1'
   services:
     oap:
       extends:
         file: ../../../script/docker-compose/base-compose.yml
         service: oap
     banyandb:
       extends:
         file: ../../../script/docker-compose/base-compose.yml
         service: banyandb
   ```

3. **Create e2e.yaml** with setup, trigger, verify sections

4. **Create expected/ directory** with expected YAML outputs for verification

5. **Create verification cases** (e.g., `simple-cases.yaml`) with swctl queries

### Verification with swctl

The `swctl` CLI queries OAP's GraphQL API:

```bash
# Query service metrics
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql \
  metrics exec --expression=service_resp_time --service-name=e2e-service-provider

# List services
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql \
  service ls

# Query traces
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql \
  trace ls --service-name=e2e-service-provider
```

### Environment Variables

Key version commits in `test/e2e-v2/script/env`:
- `SW_AGENT_JAVA_COMMIT` - Java agent version
- `SW_BANYANDB_COMMIT` - BanyanDB version
- `SW_CTL_COMMIT` - swctl CLI version
- `SW_AGENT_*_COMMIT` - Other agent versions (Go, Python, NodeJS, PHP)

### Debugging E2E Tests

**If a test fails, do NOT run cleanup immediately.** Keep containers running to debug:

```bash
# 1. Setup containers (only once)
e2e setup -c test/e2e-v2/cases/simple/jdk/e2e.yaml

# 2. Generate traffic
e2e trigger -c test/e2e-v2/cases/simple/jdk/e2e.yaml

# 3. Verify (can re-run multiple times after fixing issues)
e2e verify -c test/e2e-v2/cases/simple/jdk/e2e.yaml

# Check container logs to debug failures
docker compose -f test/e2e-v2/cases/simple/jdk/docker-compose.yml logs oap
docker compose -f test/e2e-v2/cases/simple/jdk/docker-compose.yml logs provider

# Only cleanup when done debugging
e2e cleanup -c test/e2e-v2/cases/simple/jdk/e2e.yaml
```

**Determining if rebuild is needed:**

Compare file timestamps against last package build. If any files changed after package, rebuild is needed:
```bash
# Find runtime-related files modified after package was built
find oap-server apm-protocol -type f \( \
  -name "*.java" -o -name "*.yaml" -o -name "*.yml" -o \
  -name "*.json" -o -name "*.xml" -o -name "*.properties" -o \
  -name "*.proto" \
\) -newer dist/apache-skywalking-apm-bin.tar.gz 2>/dev/null

# Find test case files modified after package was built
find test/e2e-v2 -type f \( \
  -name "*.yaml" -o -name "*.yml" -o -name "*.java" -o -name "*.json" \
\) -newer dist/apache-skywalking-apm-bin.tar.gz 2>/dev/null
```

Also compare git commit ID in binary vs current HEAD:
```bash
# Commit ID in packaged binary
unzip -p dist/apache-skywalking-apm-bin/oap-libs/server-starter-*.jar version.properties | grep git.commit.id

# Current HEAD
git rev-parse HEAD
```

**If rebuild is needed, stop e2e first:**
```bash
# 1. Cleanup running containers
e2e cleanup -c test/e2e-v2/cases/simple/jdk/e2e.yaml

# 2. Rebuild
./mvnw clean package -Pall -Dmaven.test.skip && make docker

# 3. Restart e2e
e2e setup -c test/e2e-v2/cases/simple/jdk/e2e.yaml
e2e trigger -c test/e2e-v2/cases/simple/jdk/e2e.yaml
e2e verify -c test/e2e-v2/cases/simple/jdk/e2e.yaml
```

## Git Submodules

The project uses submodules for protocol definitions and UI:
- `apm-protocol/apm-network/src/main/proto` - skywalking-data-collect-protocol
- `oap-server/server-query-plugin/.../query-protocol` - skywalking-query-protocol
- `skywalking-ui` - skywalking-booster-ui
- `oap-server/server-library/library-banyandb-client/src/main/proto` - banyandb-client-proto

Always use `--recurse-submodules` when cloning or update submodules manually.

## IDE Setup (IntelliJ IDEA)

1. Import as Maven project
2. Run `./mvnw compile -Dmaven.test.skip=true` to generate protobuf sources
3. Mark generated source folders:
   - `*/target/generated-sources/protobuf/java`
   - `*/target/generated-sources/protobuf/grpc-java`
   - `*/target/generated-sources/antlr4`
4. Import `codeStyle.xml` for consistent formatting

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

## Community

- GitHub Issues: https://github.com/apache/skywalking/issues
- Mailing List: dev@skywalking.apache.org
- Slack: #skywalking channel at Apache Slack

## Submitting Pull Requests

### Branch Strategy
- **Never work directly on master branch**
- Create a new branch for your changes: `git checkout -b feature/your-feature-name` or `git checkout -b fix/your-fix-name`
- Keep branch names descriptive and concise

### PR Title
Summarize the changes in the PR title. Examples:
- `Fix BanyanDB query timeout issue`
- `Add support for OpenTelemetry metrics`
- `Improve documentation structure`

### PR Description
Follow the PR template in `.github/PULL_REQUEST_TEMPLATE`. Key requirements:

**For Bug Fixes:**
- Add unit test to verify the fix
- Explain briefly why the bug exists and how to fix it

**For New Features:**
- Link to design doc if non-trivial
- Update documentation
- Add tests (UT, IT, E2E)
- Attach screenshots if UI related

**For Performance Improvements:**
- Add benchmark for the improvement
- Include benchmark results
- Link to theory proof or discussion articles

**Always:**
- Reference related issue: `Closes #<issue number>`
- Update [`CHANGES` log](https://github.com/apache/skywalking/blob/master/docs/en/changes/changes.md)
- Do NOT add AI assistant as co-author. Code responsibility is on the committer's hands.

## Tips for AI Assistants

1. **Always check submodules**: Protocol changes may require submodule updates
2. **Generate sources first**: Run `mvnw compile` before analyzing generated code
3. **Respect checkstyle**: No System.out, no @author, no Chinese characters
4. **Follow module patterns**: Use existing modules as templates
5. **Check multiple storage implementations**: Logic may vary by storage type
6. **OAL generates code**: Don't manually edit generated metrics classes
7. **Use Lombok**: Prefer annotations over boilerplate code
8. **Test both unit and integration**: Different test patterns for different scopes
9. **Documentation is rendered via markdown**: When reviewing docs, consider how they will be rendered by a markdown engine
10. **Relative paths in docs are valid**: Relative file paths (e.g., `../../../oap-server/...`) in documentation work both in the repo and on the documentation website, supported by website build tooling
