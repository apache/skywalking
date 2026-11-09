---
name: tal
description: Write, compile, inspect and verify Trace Analysis Language (TAL) rules for the OTLP and SkyWalking native (sw-native) inputs — rule files, the compiler and runtime map, dumping and decompiling the generated classes, the v1-v2 checker, the branch gate and the benchmark. Use when changing TAL rules, the TAL grammar/compiler, or the native trace analysis.
argument-hint: "[rules|compiler|inspect|verify|benchmark]"
---

# Trace Analysis Language (TAL)

TAL rules analyze spans. Two inputs ship:

| Input | `span` is | Other roots / data | Builds through |
|---|---|---|---|
| `otlp` (default) | the OTLP wire `Span` | `meta` (`SpanMetadata`), `attr`/`hasAttr`, `resource`, `scopeAttr`, `event`/`eventAttr` | span metadata, `metrics {}` and `source <Scope> {}` at the statement, `persist {}` |
| `sw-native` | the agent's protobuf `SpanObject` | `segment()` (`SegmentObject`) and `segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()`, `segmentSampled()`, `segmentBytes()`; `tag`/`hasTag`/`tagList`/`tagMap` | `build {}` with `source <Scope> {}` and `metrics {}`, plus `first {}`, `segmentDef`, `writeNetworkAlias` |

A construct or function used under the wrong input is a compile error that names the inputs it belongs to.

User docs (keep them in sync with any language change): `docs/en/concepts-and-designs/tal.md` (language + the
input comparison table + functions for every input), `tal-otlp.md`, `tal-sw-native.md`; setup in
`docs/en/setup/backend/trace-analyzer.md`, `native-trace.md`, `native-trace-analysis.md`.

## Rule files

- Location: `oap-server/server-starter/src/main/resources/tal/` (shipped as `config/tal/`).
- OTLP: `trace-analyzer.default.talFiles`; native: `trace-analyzer.default.nativeTalFiles` (`SW_TRACE_NATIVE_TAL_FILES`).
  The native file order is a contract: rules run in file order, then rule order, and `build {}` blocks flush rule by
  rule, which fixes the order sources reach OAL.
- The native rules port the former `agent-analyzer` listeners with three documented differences: a repeated tag key
  reads its first value, a bare host also matches a gateway listed at port 80 (a feature shared with OTLP), and AI
  evaluation samples are taken in the build phase. Keep the checker's explicit expectations for those. Each rule's
  comments name the listener method it reproduces; the moved Java lives on as the test baseline in
  `test/script-cases/trace-analysis-listener-v1/`.
- Generated class names are `{file}_L{line}_{rule}` (e.g. `native_rpc_L28_native_rpc_entry`); logs go to
  `org.apache.skywalking.trace.analysis.script.<file>`.

Language traps when writing rules:
- `def` declares, `=` assigns. A `def` inside an `if` branch is method-level; a `def` inside `foreach` belongs to one
  iteration and is null at its start.
- There are no expression statements and no subroutines. A method call result must be assigned (`def x = list.add(...)`),
  and a body needed in two branches is written twice (the native RPC entry rule repeats its `build {}`).
- `foreach (x in <list>)` walks a `java.util.List`, typed from the getter's generic type; `list[n]` returns `Object`.
- Compare a Java enum or a `Layer` with a string literal (`span.spanType == "Entry"`); the literal is checked at compile.
  `isEmpty(list)` covers null or empty lists.

## Compiler and runtime map

All under `oap-server/analyzer/trace-analyzer/src/main/`:

| Part | Where |
|---|---|
| Grammar | `antlr4/.../tal/rt/grammar/TALLexer.g4`, `TALParser.g4` |
| Parse → model | `java/.../v2/compiler/TALScriptParser.java`, `TALScriptModel.java` |
| Codegen (Javassist) | `TALClassGenerator` (class, fields, methods), `TALBlockCodegen` (blocks/statements), `TALDefCodegen` (`def`/casts), `TALValueCodegen` (values/comparisons), `TALSpanRootResolver` (getter chains/functions) |
| Functions | `TALFunctionRegistry`; input functions = public methods of a `spi/TALSourceTypeProvider` whose first parameter is `TalRuntimeHelper`; module functions = `TALFunctionProvider` (server-core) via ServiceLoader; shared helpers `compiler/rt/TalGeneralFunctions`, `TalConfigFunctions` |
| Runtime helpers | `compiler/rt/TalValueConversions`, `TalRuntimeHelper`, `TalNativeOutputs` |
| Runners | OTLP: `provider/span/TraceAnalyzerServiceImpl`; native: `provider/nativetrace/SwNativeRuleRunner` (parseSegment work, per-span runs, build phase) |
| Per-run state | `dsl/ExecutionContext`: `init(...)` for OTLP, `bindNativeRun(...)` for native (resets only what a native rule can change) |

Generated code rules (Javassist): no imports, so generated source uses fully qualified names, including `java.util.Map`. There is no autoboxing, so primitives go through the `TalValueConversions` overloads. The code must stay JDK 11 compatible.

Performance rules for the native path (measured; keep them when changing codegen):
- **Function objects** are fields of the generated class, set once at compile from the registry the rule compiled against. Never look one up per run.
- **Services:** the native provider binds the analysis configuration, server clusters and the alias cache once, in `TALSourceTypeProvider.bind(ModuleManager)`, falling back to `h.spec().service(...)` only when a field is unbound (the default registry binds with a null manager). The naming functions (`formatServiceName` and the others, in `TalConfigFunctions`, for every input) use the `NamingControl` that `FilterSpec` caches; helper and module providers have their own `bind`.
- **Literal comparisons:** an enum vs a literal compiles to `==` on the constant, a String vs a literal to `"lit".equals(x)`, and a built-in `Layer` literal to `Layer.X`. Dynamic layers stay a runtime `toLayer(...)`.
- **Per-run allocation:** add none in the native runner. It runs every rule on every span.

## Inspect the generated classes

```bash
# Dump .class + the Javassist input source of every compiled rule
SW_DYNAMIC_CLASS_ENGINE_DEBUG=1 ./mvnw -o test -pl test/script-cases/trace-analysis-v1-v2-checker \
  -Dtest=NativeTraceComparisonTest -Dcheckstyle.skip
ls ~/.m2/repository/org/apache/skywalking/server-core/tal-rt/   # one level above the server-core jar's directory; a dist writes <dist>/tal-rt/

# Decompile what actually runs (IntelliJ's FernFlower needs a recent JDK; its bundled JBR works)
J="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java"
"$J" -cp "/Applications/IntelliJ IDEA.app/Contents/plugins/java-decompiler/lib/java-decompiler.jar" \
  org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=1 -rsy=1 <classes...> <out-dir>
```

Only the v2 side compiles TAL: one v2 side per profile for the ordinary passes, plus a fresh one per difference fixture. Repeated class names in one JVM get `_2`, `_3`, … suffixes; pick one copy per rule. To review a port, compare the decompiled class with the listener method its rule names.

## Verify

```bash
# Once, and after any cross-module change: the checker depends on server-core, agent-analyzer, ai-evaluation and the
# listener baseline module, so install the backend reactor first
./mvnw -o -q clean flatten:flatten install -Pbackend -Dmaven.test.skip=true -Dcheckstyle.skip
# After a trace-analyzer-only change
./mvnw -o -q flatten:flatten install -pl oap-server/analyzer/trace-analyzer -Dmaven.test.skip -Dcheckstyle.skip
./mvnw -o test -pl oap-server/analyzer/trace-analyzer -Dcheckstyle.skip            # language + native unit tests
./mvnw -o test -pl test/script-cases/trace-analysis-v1-v2-checker -Dcheckstyle.skip  # v1 listeners vs v2 rules
```

The v1-v2 checker (`test/script-cases/trace-analysis-v1-v2-checker`):
- **What it compares:** the two engines at TAL's outputs: sources, records, stateful collaborator calls, log lines and failures. It does not run OAL dispatch or persistence.
- **Coverage:** 21 profiles of configuration variants run over constructed `SegmentObject` fixtures (`CheckerFixtures`), cold and warm passes.
- **Documented differences** are separate fixtures with explicit expectations.
- **Logic review:** the v1 events are written to `target/checker-events-<profile>.txt`. Read them; nothing is committed as expected data.
- **Branch gate:** after all profiles, a JaCoCo gate fails on any missed branch of the moved listener classes that isn't listed, with its exact count, in `src/test/resources/checker-allowed-misses.yml`. A newly covered listed branch fails it too.

## Benchmark

```bash
./mvnw -o test -pl test/script-cases/trace-analysis-v1-v2-checker -Dtest=NativeTraceBenchmark \
  -Dsurefire.failIfNoSpecifiedTests=false -Dcheckstyle.skip
```

`NativeTraceBenchmark` is JMH: two forks, logging off, JaCoCo removed from the forks, plain module lookups, and every source consumed by a per-thread blackhole. It measures segments per second and bytes per segment for the fixture mix, a wide 40-span segment and 4 threads, with cold and warm alias caches.

Compare Java and rules within one run. For a CPU profile, add `-XX:StartFlightRecording` through `-jvmArgs` on a direct JMH run, then use `jfr view hot-methods`. JFR line attribution inside inlined code can be skid: read inclusive stacks before blaming one line.
