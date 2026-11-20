---
name: graalvm-distro-impact
description: What skywalking-graalvm-distro, the GraalVM native-image build of this repo, depends on and how it overrides upstream (build-time DSL compilation, same-FQCN replacement classes, generated config loading, the fixed module set, native-image metadata); the native-image concerns that apply to any class (run-time class generation, reflection, object binding and serialization, dynamic proxies, resources, run-time code discovery, native code, JVM management, build-time environment, static state, security providers and TLS trust, Unsafe and finalizers, logging internals); what each kind of change means for it; what to consider when changing OAP code, what to check when reviewing a change, and what the PR template's "GraalVM distro impact" item states. Use when changing or reviewing OAP code (DSL engines, replaced classes, modules or config classes, application.yml, bundled rules, reflection, resources, run-time class generation) and when filling that PR item.
---

# GraalVM Distro Impact

[skywalking-graalvm-distro](https://github.com/apache/skywalking-graalvm-distro) builds the OAP as a GraalVM native
image. It pins this repo as its `skywalking/` submodule and never patches it; everything native-specific lives in
the distro. A change here that the distro can't take yet is normal and doesn't block the PR. The PR states the impact
so the distro's next sync knows what to adapt.

Use this skill three ways: when changing OAP code ([When changing code](#when-changing-code)), when reviewing a
change ([When reviewing a change](#when-reviewing-a-change)), and when filling the PR template's item
([What the PR states](#what-the-pr-states)). The sections before them are the context all three need.

**Scope: only what this change does.** Judge the diff of this change (`<base>...HEAD`) and nothing else. An impact
that is already on master, from an earlier change the distro hasn't synced yet, is not this change's concern, nor is
one already reported to the distro's maintainers: the distro deals with those at its next sync. Leave them out of this
PR's item, and don't count a distro check that fails only because the distro trails master.

Everything this skill says about the distro is a snapshot (distro `main` f78c20f, 2026-11-20) for orientation: the
replaced classes, the metadata and the module set change with every distro sync. Decide against the latest distro, as
[Keep a current distro clone](#keep-a-current-distro-clone) describes.

## Keep a current distro clone

A clone of the distro, kept current, is a good way to cross-check a change: this skill's tables are a snapshot, while
the clone has the latest replaced classes, metadata, module set and packaging. Don't clone it yourself:

1. **Check your memory first.** If it records the user's choice from an earlier session, use it (a clone that no
   longer exists, or isn't a distro clone, means asking again). Otherwise, skim for an existing clone in the folder
   that holds this repo (any name; it is found by its `origin`):

   ```bash
   for d in "$(git rev-parse --show-toplevel)"/../*/; do
     case "$(git -C "$d" remote get-url origin 2>/dev/null)" in *skywalking-graalvm-distro*) (cd "$d" && pwd -P) ;; esac
   done
   ```

2. **Ask the user.** If you found one, ask whether to use it. If you found none, ask where it should be cloned
   (`https://github.com/apache/skywalking-graalvm-distro.git`), or where an existing clone is; clone only where and
   when they say. Without a clone, use the snapshot in this skill and say so.
3. **Remember the choice.** Once the user has chosen (this clone, a folder to clone into, or no clone), record it in
   your agent's own memory, whichever agent you are (Claude, Gemini, Codex or another); most coding agents keep a
   memory place, so use that. It is a personal path: never write it into a file of this repo.
4. **Fetch it before every use,** and read `origin/main`: it is the latest after the fetch even when the clone sits on
   a feature branch. Pull only a clone that is on `main`, and switch nothing.

   ```bash
   D=<the clone the user confirmed>
   git -C "$D" fetch origin
   git -C "$D" show origin/main:<path>
   git -C "$D" grep <text> origin/main -- <paths>
   ```

[When reviewing a change](#when-reviewing-a-change) runs its checks against it.

| Question | Where the latest answer is in the distro |
|---|---|
| Which classes of this repo it replaces | The `.java` files under `oap-libs-for-graalvm/*/src/main/java`, `oap-graalvm-server/src/main/java` and `build-tools/precompiler/src/main/java` whose package path is a class of this repo; `oap-graalvm-server/src/test/resources/replacement-source-sha256.properties` pins most of them |
| Which modules and providers it ships | `build-tools/build-common/src/main/java/org/apache/skywalking/oap/server/buildtools/common/AcceptedModules.java`, `oap-graalvm-server/src/test/resources/provider-inventory.properties`, `docs/distro-policy.md` |
| Its `application.yml` | `oap-graalvm-server/src/main/resources/application.yml` |
| The config classes it loads | `build-tools/precompiler/src/main/resources/META-INF/module-config-classes.txt` |
| What it registers for reflection and resources | `oap-graalvm-native/src/main/resources/META-INF/native-image/org.apache.skywalking/oap-graalvm-native/` (`reachability-metadata.json`, `*-reflect-config.json`), and what `build-tools/precompiler/src/main/java/org/apache/skywalking/oap/server/buildtools/precompiler/Precompiler.java` generates |
| Its native build arguments | `oap-graalvm-native/pom.xml` |
| The files it packages from this repo | `oap-graalvm-server/src/main/assembly/distribution.xml`, `oap-graalvm-native/src/main/assembly/native-distribution.xml` |
| The files of this repo its tests and e2e cases read | `oap-graalvm-server/pom.xml`, `build-tools/precompiler/src/test/`, `oap-graalvm-server/src/test/`, `test/e2e/cases/` |
| What broke it on past syncs | `.claude/skills/sync-submodule/SKILL.md` (its sync checklist and "Common Pitfalls") |

## GraalVM limits, and how the distro deals with them

A native image is a closed world: everything that runs is fixed when the image is built. No class can be generated at
run time that wasn't known at build time; reflection, resources, proxies, serialization and JNI work only with
metadata recorded at build time (or registrations Native Image resolves itself); there is no classpath to scan and no
jar or classpath directory at run time. Code of this repo that relies on any of these has to be handled in the
distro, by one of the mechanisms below. Some mechanisms also serve the distro's own choices
rather than a native limit: it runs only compiled rules (no rule files at run time), loads config through generated
setters, and ships a fixed module set.

The distro's principles:

- **This repo is never patched for the distro.** Everything native-specific lives in skywalking-graalvm-distro; a
  change here that would help it is an ordinary, distro-agnostic upstream PR.
- **Generate at build time what upstream generates at run time,** with upstream's own compilers, so the generated
  classes are the ones the JVM OAP would generate, under the same names.
- **Keep upstream's behavior.** A replacement keeps the API callers use; comparison and parity tests hold the
  precompiled rules and the registry to what upstream's engines and loaders produce.
- **Fail the build where upstream fails the boot,** e.g. a rule that doesn't compile.

| Limit | Distro mechanism | What the distro does | What it relies on here |
|---|---|---|---|
| No class unknown at build time can be defined at run time | Build-time generation | `build-tools/precompiler` runs the OAL, MAL, LAL, TAL and Hierarchy compilers and `MeterSystem` at build time and packs the generated classes into a jar. It reads the bundled rule files (MAL, LAL, TAL and Zabbix through upstream's loaders, the hierarchy and layer files directly) and generates the static rule registry (`CompiledRules`), so at run time the compiled rules come from it, not from rule files | The generators' build-time API (below), deterministic class names (`DslSourceRef`, `{yaml}_L{line}_{name}`), generated classes built by their no-arg constructor with any initialization upstream does afterwards (TAL's function objects) recorded so the distro can replay it, and the rule data classes (`Rule`, `LALConfigs`, `TALConfigs`, `ZabbixConfig`, `LayerDefinition`) with their setters |
| Upstream code that can't run natively, but whose API other code calls | Same-FQCN replacement | 30 upstream classes are replaced by the distro's own copy with the same fully-qualified name: 26 shaded into `*-for-graalvm` jars, 4 (`YamlConfigLoaderUtils`, `FieldsHelper`, `ResourceUtils`, `LoggingConfigWatcher`) in `oap-graalvm-server`; the originals are filtered out | Every caller finds the members it calls on the copy; the native-unfriendly code sits in a class small enough to copy |
| Upstream designs the distro serves differently: run-time rule compilation and hot-update (impossible natively), rule files and reflective config loading at run time (the distro's choice: compiled rules only, generated setter-based loading) | Reimplementation in the distro | Distro-only code: its boot (`GraalVMOAPServerStartUp`), a generated setter-based config loader (`ConfigInitializerGenerator` writes the `YamlConfigLoaderUtils` replacement), its own `application.yml` with compile-time rule activation (`CompiledRuleActivation`, `CompiledRulesCheck`), a read-only rule catalog (`BundledRuleCatalogHandler`), and the `graalvm-unsupported-admin` module standing in for runtime-rule | Seams it can plug into: the module / provider SPI, service interfaces, setters on config classes |
| No reflection, resources or proxies without metadata | Native-image metadata | Generated by the precompiler's scans (DSL classes, handlers, GraphQL, covered JSON packages, protobuf closures, config classes) plus hand-kept JSON under `oap-graalvm-native/.../native-image/` | Code following the scanned patterns ([below](#what-the-native-image-metadata-covers-by-itself)) |
| No classpath to scan | Build-time manifests | The precompiler writes `META-INF/annotation-scan/*.txt`, which the `AnnotationScan`, `SourceReceiverImpl` and `MeterSystem` replacements read | Declarative markers (annotations, interfaces, SPI) instead of ad-hoc scanning |
| No classpath directory or jar at run time | Packaging and resource globs | The assemblies copy runtime config files to `config/`; globs embed resources; the `ResourceUtils` replacement reads `config/` first, then the embedded copy | Runtime config read through `ResourceUtils` from `config/` |
| Features left out: native-incompatible ones (runtime-rule hot-update) and ones the distro's policy excludes (storage other than BanyanDB, other cluster and configuration providers) | Leave out, or stub | A fixed module set: only accepted providers are on the classpath (`server-starter-for-graalvm` excludes all of `server-starter`'s dependencies), booted through the SPI as `application.yml` names them; runtime-rule mutations are answered with HTTP 501 by `graalvm-unsupported-admin` | Each such feature in its own module or provider; the distro's `AcceptedModules` lists what it ships, and `provider-inventory.properties` marks every other provider `NOT_ACCEPTED` |

### Module set

Storage BanyanDB only; cluster standalone and Kubernetes; configuration Kubernetes ConfigMap or none; every other
module (receivers, fetchers, analyzers, query, admin, alarm, exporter, telemetry, health checker, AI pipeline), except
`receiver-runtime-rule` (hot-update
needs run-time Javassist; the distro's `graalvm-unsupported-admin` answers its mutations with HTTP 501, and serves
the read-only rule catalog from the precompiled rule sources). Live list: the distro's `docs/distro-policy.md` and
`oap-graalvm-server/src/test/resources/provider-inventory.properties`.

### The replaced classes

A snapshot of what the distro has patched so far, the most certain impact when edited; it is not the boundary of
native-image risk ([Native-image concerns in any class](#native-image-concerns-in-any-class)). The latest list is in
your distro clone ([Keep a current distro clone](#keep-a-current-distro-clone)): the same-FQCN `.java` files under
`oap-libs-for-graalvm/*/src/main/java`, `oap-graalvm-server/src/main/java` (outside
`org.apache.skywalking.oap.server.graalvm`) and, build-time only, `build-tools/precompiler/src/main/java`. Of these,
`replacement-source-sha256.properties` pins the upstream source of 27; `FieldsHelper`, `ResourceUtils` and
`LoggingConfigWatcher` are not pinned.

| Upstream class | Why the distro replaces it |
|---|---|
| `OALEngineLoaderService` | Registers the precompiled OAL metrics, dispatchers and disabled sources instead of running the OAL engine |
| MAL `v2.dsl.DSL`, `FilterExpression` | Return the precompiled `MalExpression` / filter by expression text / filter literal (build-time copies in the precompiler capture the classes) |
| `Rules` (MAL), `LALConfigs`, `TALConfigs`, `ZabbixConfigs`, `LayerExtensionLoader` | Read the rule registry instead of rule files |
| LAL `v2.dsl.DSL` | Returns the precompiled `LalExpression` by source coordinates (`lal/<file>.yaml:<line>`), falling back to the rule name, with its effective input type |
| `TalDSL` | Returns the precompiled `TalExpression` by coordinates (`tal/<file>.yaml:<line>`) after checking the rule name, DSL, input type and alias and metric prefix match, sets its function-object fields from the function registry by recorded key, creates its metrics (`create` + `limitLabels`) |
| `HierarchyDefinitionService`, `HierarchyService` | Hierarchy definition and precompiled matching rules from the registry; the copy's `MatchingRule` has no `match(Service, Service)`, so the `HierarchyService` copy calls `getMatcher().apply(...)` |
| `MeterSystem` | Meter functions from a manifest and precompiled meter classes, no Guava classpath scan or Javassist (a build-time copy in the precompiler generates and exports the classes) |
| `AnnotationScan`, `SourceReceiverImpl` | Annotation and dispatcher manifests instead of Guava `ClassPath` scanning |
| `YamlConfigLoaderUtils` | Generated, setter-based config loading |
| `CoreModuleConfig`, `AnalyzerModuleConfig`, `LogAnalyzerModuleConfig`, `OtelMetricReceiverConfig`, `EnvoyMetricReceiverConfig`, `EBPFReceiverModuleConfig`, `AWSFirehoseReceiverModuleConfig`, `CiliumFetcherConfig`, `HealthCheckerConfig` | Class-level `@Setter` added for the generated loader |
| `DynamicSslContext` | gRPC TLS on the JDK provider instead of OpenSSL (`netty_tcnative`) |
| `VirtualThreads` | Direct JDK 25 API instead of reflection |
| `FieldsHelper` | `MethodHandle` instead of run-time `LambdaMetafactory` |
| `ResourceUtils` | Reads `config/` from the filesystem first, then embedded resources (no classpath directory in a native image) |
| `LoggingConfigWatcher` | No cast to `XmlConfiguration` (native Log4j2 may run on `DefaultConfiguration`) |

A replacement is a copy: upstream changes to the original never reach it. The copy breaks callers when upstream adds
or changes a member they call. The callers are this repo's classes, compiled here against the original, so the
distro's own build doesn't see it: it surfaces at run time as `NoSuchMethodError` / `NoSuchFieldError` (native-image
analysis may report it earlier). The copy silently diverges when upstream changes logic it duplicated.
Example: #14255 added `getOtlpLogSearchableTags()` to `OtelMetricReceiverConfig`, called from
`OtelMetricReceiverProvider#start()` when the `otlp-logs` handler is enabled (the distro's default); the distro's copy
lacks it, so the distro's boot would fail there until the copy gets it.

### Build-time API the precompiler calls

- OAL: `OALEngineV2#start` and `#setStorageBuilderFactory`, with `getClassGeneratorV2().setOpenEngineDebug(true)` and
  `OALClassGeneratorV2.setGeneratedFilePath(...)` to export the classes.
- MAL: the precompiler runs this repo's `MetricConvert` over each rule file; its `DSL.parse` and `FilterExpression`
  calls reach the precompiler's build-time copies of those two classes, which call `MALClassGenerator`
  (`setClassOutputDir`, `setClassNameHint`, `setSourceRef`, `setContent`, `compile`, `compileFilter`). A change to how the MAL
  runtime calls `DSL` / `FilterExpression` reaches both the build-time and the run-time copies.
- LAL / TAL / Hierarchy generators: `setClassOutputDir`, `setClassNameHint`, `setSourceRef`, `compile`; LAL
  `setContent`, `setInputType`, `setOutputType`, `getEffectiveInputType`; TAL `setContent`, `setInputType`,
  `setInputAlias`, `setMetricPrefix`, `setFunctionRegistry`, `getMetricDeclarations`, `createMetrics`;
  `TALFunctionRegistry#discover` / `bind`; `DslSourceRef#ofRule`.
- `DSLDebugCodegenSwitch#enableInjection`, so the precompiled OAL / MAL / LAL classes carry the live-debugger probes.
- The loaders it reads rule files through: `Rules#loadRules`, `LALConfigs#load`, `TALConfigs#load`,
  `ZabbixConfigs#loadConfigs`, `LayerExtensionLoader#load`.
- Two private fields it reaches by reflection: `OALClassGeneratorV2.IS_RT_TEMP_FOLDER_INIT_COMPLETED` (set) and
  `DisableRegister.disableEntitySet` (read). Renaming either breaks the precompiler.

The distro instantiates the precompiled classes itself, through their no-arg constructors (DSL classes by constructor
references in the generated registry, meter classes reflectively): whatever upstream sets on a generated instance after
compiling (SWIP-17's TAL function-object
fields, set by reflection from the `TALFunctionRegistry`) has to be replayed by the distro.

### What the native-image metadata covers by itself

- Classes the DSL compilers generate, OAL metrics / builders / dispatchers, meter classes, storage builders, and
  annotation- and interface-scanned classes (`@Stream`, `@ScopeDeclaration`, `SourceDispatcher` implementations, …).
- Armeria handlers with `@Get` / `@Post` / `@Path` methods, the `@Decorator` targets they name, and the
  `ExceptionHandlerFunction` implementations in this repo.
- GraphQL resolvers, and the types of `query-protocol/*.graphqls`.
- Jackson / Gson POJOs in `org.apache.skywalking.oap.query.*.entity.*`, `org.apache.skywalking.oap.server.admin.*.response.*`,
  `admin.status` `Debugging*Rsp` and `core.alarm.provider.status.*`. A JSON body class elsewhere needs a hand entry,
  unless another scan (GraphQL types, a protobuf closure) already covers it; without one, Jackson can answer HTTP 400
  "No serializer found" and Gson can print `{}`.
- Protobuf types reachable (descriptor closure) from the LAL and TAL input types, the OTLP export requests,
  `ExportTraceServiceResponse`, Tempo's `TraceByIDResponse` and `LogData`. `JsonFormat` on a type outside those
  closures needs adding, or it fails with "Generated message class ... missing method".
- The config classes in the distro's `module-config-classes.txt` (no-arg constructor for `ModuleDefine#prepare`).

Not covered by itself: `Class.forName` / `newInstance` of other classes (e.g. a non-`@Stream` class registered with
`IWorkerInstanceSetter.put(..., Class)`, rebuilt by `RemoteHandleWorker` on the receiving node), a class an Armeria
`@RequestConverter` names (not collected), a new `LALOutputBuilder` (a hand list in the precompiler), a new classpath resource read at run time (embedded by glob), a new runtime config
file under `config/` (packaged by the distro's `distribution.xml` and `native-distribution.xml`, and embedded by a
resource glob too: the distro's `ResourceUtils` reads `config/` first, then the embedded copy).

## Native-image concerns in any class

The replaced classes are where the distro has already patched this repo, and the list grows whenever an upstream class
hits a concern the distro can't cover with metadata. The concerns themselves apply to every class this repo changes or
adds: a native image is built under a closed-world assumption, so a class doing one of these things works on the JVM
and fails in the native image unless the distro covers it. A class not on the replaced list is not safe by default.

As of distro `main` f78c20f, the image is built with `--no-fallback`, `-H:+AddAllCharsets` and
`--enable-url-protocols=https,http`, without `--enable-monitoring`, and without asking for build-time class
initialization (Native Image still initializes JDK classes, and application classes it proves safe, at build time).
Its own metadata registers reflection (some entries with JNI access), resources and one resource bundle, and no dynamic
proxies or Java serialization; libraries can bring their own, e.g. through the GraalVM reachability-metadata
repository. Native Image also registers by itself the accesses it resolves at build time (constant class, member and
interface names), so a pattern below is a reason to check, not a verdict. Upstream's main code makes no direct use of
dynamic proxies, object-stream serialization, JNI, `getProtectionDomain` / `getCodeSource`, scripting engines or
`ManagementFactory` today, so adding one is always worth naming. (`WorkPath` does locate the OAP jar from its own
class's resource URL, which the native image doesn't have; only the DSL debug file dump calls it.)

| Concern | What to look for | How the distro handles it today | What a change needs |
|---|---|---|---|
| Run-time class generation | Javassist (`ClassPool`, `CtClass#toClass`), ASM / ByteBuddy / cglib, `ClassLoader#defineClass`, `Lookup#defineClass` / `defineHiddenClass`, a direct `LambdaMetafactory` call, `javax.tools` / `ToolProvider` compilers, most `javax.script` engines | No new class can be defined at run time. DSL generation runs at build time, `FieldsHelper` is replaced, runtime-rule hot-update isn't shipped. Ordinary lambdas and method references are fine | A build-time step or a replacement in the distro |
| Reflection | `Class.forName`, `newInstance` / `getDeclaredConstructor().newInstance()`, `get(Declared)Method(s)` / `Field(s)` / `Constructor(s)`, `Method#invoke`, `Field#get` / `set`, `setAccessible`, `MethodHandles.Lookup#find*` / `unreflect*` on names not known at build time, annotations read from classes found at run time | Works only for registered classes and members, or accesses Native Image resolves itself (constant names): what the precompiler scans ([above](#what-the-native-image-metadata-covers-by-itself)), and the hand-kept `oap-graalvm-native/.../reachability-metadata.json` and `*-reflect-config.json` files (fabric8, Netty, Jackson, Armeria, Envoy / gRPC, Log4j2, Guava) | An entry for each class or member reached this way and not yet registered; search the distro's metadata for the class name first |
| Object binding (serialization) | Jackson `readValue` / `writeValue` / `convertValue`, Armeria `@ProducesJson` / `@ConsumesJson` / `@RequestObject` / `HttpResponse.ofJson`, Gson `toJson` / `fromJson` of objects, SnakeYAML `loadAs` or a `Constructor` bound to a class, protobuf `JsonFormat`, Java serialization (`Serializable` with `ObjectInputStream` / `ObjectOutputStream`) | JSON: the covered packages and protobuf closures ([above](#what-the-native-image-metadata-covers-by-itself)). Java serialization: nothing registered. Parsing into maps and lists (SnakeYAML `SafeConstructor`, as `ServerClusterConfig` does; Jackson `readTree`; Gson `JsonParser`) needs no entries for this repo's classes | Entries for the bound classes (constructors, fields, accessors); serialization metadata for Java serialization |
| Dynamic proxies | `java.lang.reflect.Proxy#newProxyInstance`, a library that proxies an interface at run time | Nothing in the distro's own metadata; a constant interface list is registered by Native Image itself | A proxy entry naming the interfaces, in order |
| Resources | `Class#getResource(AsStream)`, `ClassLoader#getResource(s)`, `ResourceUtils.read` / `getPath` / `getPathFiles`, `ResourceBundle#getBundle` | Embedded when a pattern matches (globs in `reachability-metadata.json`, regex patterns in the precompiler's `resource-config.json`), or registered by Native Image itself for `Class#getResource(AsStream)` with a constant class and name; a resource bundle needs a bundle entry (`i18n.Parsing` is registered) and its locales; runtime config is read from `config/` first, then the embedded copy (`ResourceUtils` replacement). Embedded resources serve `read` / `readToStream` only: `getPath`, `getPathFiles` and `getDirectoryFilesRecursive` work on real files under `config/` | A resource glob or bundle entry; a `config/` file, and every directory listed at run time, packaged in both distributions |
| Discovering code at run time | Guava `ClassPath`, `ClassLoader#getResources` over directories, walking jar files, `getProtectionDomain().getCodeSource()` or a class's resource URL (`WorkPath`) to find a jar or the home directory | The image has no run-time classpath to scan and can't load new code from jars; `AnnotationScan`, `SourceReceiverImpl` and `MeterSystem` read build-time manifests instead | A build-time manifest or a replacement |
| Service providers | `ServiceLoader#load` | The providers on the build classpath of a service interface the image reaches are registered; the build classpath holds only the shipped modules | Nothing for the SPI itself; a provider in a module the distro doesn't ship is absent |
| Native code | JNI, `System#loadLibrary` / `load`, OpenSSL / `netty-tcnative`, JNA | Some JNI access is registered (mostly Netty's); gRPC TLS runs on the JDK provider instead of OpenSSL (`DynamicSslContext`) | JNI metadata plus the native library packaged and loadable, or a pure-Java path |
| JVM management and agents | `ManagementFactory` / MXBeans, JMX, JFR, `java.lang.instrument`, HotSpot `-XX` options | Some `java.lang.management`, `com.sun.management` and `jdk.management.jfr` types are registered, but the image is built without `--enable-monitoring`, so JMX and JFR aren't available; GC names differ from HotSpot's; no Java agents; an unsupported `-XX` option fails the boot | Check the native image supports and reports what the code reads; JMX or JFR needs a monitoring build option |
| Build-time environment | Default encodings, locale data, URL protocols, charsets | All charsets are included; the default encodings (`native.encoding`, `sun.jnu.encoding`, `stdout.encoding`) are fixed when the image is built; only the build machine's locale data is included (no `-H:IncludeLocales`), so formatting for another locale falls back to it; only the URL protocols the image enables work (`http`, `https` and the defaults) | Enabling another URL protocol in the distro's native build |
| Static state from build time | Static initializers that read configuration, the environment, files, time or randomness, or start threads or open handles | Nothing is forced to build-time initialization; Native Image initializes at build time only what it proves safe, and a class initialized there keeps the build machine's static state | Keep environment-dependent work out of static initializers |
| Security providers and TLS trust | JCA providers (`Security#addProvider`, a third-party provider), custom service types, keystore types, the default trust store | The JDK providers the image includes; once a service type's `getInstance` is reachable, its available implementations are registered, so a configurable algorithm name needs nothing more; the default CA trust store is the build-time JDK's unless overridden at run time (`-Djavax.net.ssl.trustStore`) | Check a new provider, custom service type, keystore type or trust setting works natively; name it |
| `Unsafe` and finalizers | `sun.misc.Unsafe` field offsets or `allocateInstance`, cleanup in `finalize()` | Native Image recomputes ordinary `Unsafe` field offsets itself; finalizers never run | `Cleaner` or explicit close instead of `finalize()`; name unusual `Unsafe` use |
| Logging internals | Log4j2 plugins (appenders, layouts, lookups), casts to a specific `Configuration` type | Plugins through the hand-kept `log4j2-reflect-config.json`; a console-only `log4j2.xml`; `LoggingConfigWatcher` replaced | Metadata for a new plugin; no assumption about the configuration type |

## Deciding the impact

| The change | Impact | What the distro has to do |
|---|---|---|
| Edits a replaced class (above) | Impacted, unless it only touches comments | Diff the change into the copy; for a new member a caller uses, it's a boot or run-time failure until then |
| Changes a DSL engine: OAL / MAL / LAL / TAL / Hierarchy compiler or runtime, `MeterSystem`, the debug codegen | Impacted. Distro code changes when it changes the build-time API above, what a generated class needs after its no-arg constructor, the rule data classes, the lookup keys (expression text, `file:line` coordinates, class names), or adds a `TALFunctionProvider` / `TALSourceTypeProvider` module. Otherwise mechanical: the precompiler compiles with this repo's engine and the comparison tests recheck it | Adapt the precompiler, the registry generator or the replacements; otherwise recompile and rerun the comparison tests |
| Adds or removes a module or provider | Impacted. Of a kind the distro ships: accept and wire it (`AcceptedModules`, `provider-inventory.properties`, `oap-graalvm-server/pom.xml`, an `application.yml` section; a missing config loader branch fails boot with "Unknown config type"). Of an excluded kind (the None row): only a `provider-inventory.properties` entry, mechanical | As stated |
| Adds, removes or retypes a `ModuleConfig` field | Impacted when the class belongs to a provider the distro ships | Regenerate `YamlConfigLoaderUtils`; a non-final field with no setter needs a replacement copy with `@Setter` (a class-level `@Setter` here avoids it) |
| Changes `application.yml` | Impacted when it adds, removes or renames a key or default of a shipped module, or adds a rule-selecting setting (a list of rule files) | Mirror it in the distro's `application.yml`; a rule-selecting setting goes into `CompiledRuleActivation` |
| Adds, removes or edits bundled rule files (`oal/`, `otel-rules/`, `meter-analyzer-config/`, `log-mal-rules/`, `lal/`, `tal/`, `envoy-metrics-rules/`, `telegraf-rules/`, `zabbix-rules/`, `hierarchy-definition.yml`, `layer-extensions.yml`) | Impacted, mechanical: the distro compiles them as they are | Refresh checksums, inventories and comparison-test inputs; a rule that needs a newer engine feature waits for the sync |
| Adds, in any class, a pattern from [Native-image concerns in any class](#native-image-concerns-in-any-class): run-time class generation, reflection, object binding, a dynamic proxy, a resource, run-time code discovery, native code, JVM management, a Log4j2 plugin, a new URL protocol, static state from build time, a security provider or trust setting, `Unsafe` or a finalizer | Impacted, native only (the JVM never notices), unless [the metadata already covers it](#what-the-native-image-metadata-covers-by-itself) or Native Image registers it by itself (constant names) | What that table's last column says |
| Moves, renames or removes a file the distro's assemblies copy from this repo: the runtime config files under `oap-server/server-starter/src/main/resources/`, `dist-material/log4j2.xml` (JVM distribution), `dist-material/alarm-settings.yml`, `dist-material/release-docs/` (LICENSE, NOTICE) | Impacted. Edits in place are picked up as they are | Repoint the assembly |
| Changes `oap-server/server-starter/pom.xml` | Impacted when it changes a dependency, or adds a `maven-jar-plugin` exclude (a new runtime config file under `config/`) | Mirror the dependency in the distro's poms; package the new config file in both distributions and add its resource glob |
| Changes files the distro's tests read from this repo: `docs/en/concepts-and-designs/tal-otlp.md` and `docs/en/setup/backend/otlp-trace-practices/{gateway-overview,gateway-canary,gateway-nginx,database-and-cache,message-queue}.md` (scripts `TALDocScriptsTest` compiles), `CheckerFixtures.java` and `CheckerDifference.java` of `test/script-cases/trace-analysis-v1-v2-checker/` (`NativeTALComparisonTest`), `otel-receiver-plugin/src/test/resources/tal/otlp-trace-get-api-cart.json` (`TALRecordedTraceComparisonTest`), the `test/e2e-v2/cases/` and `test/e2e-v2/script/` files its e2e cases reference by relative path, and the `test/e2e-v2/java-test-service/` mock services its CI builds | Impacted, test-side, only when the distro's test or case has to change: a moved or renamed file, a moved snippet, a fixture shape its test parses, or an expected file that now expects behavior the distro doesn't have yet. An edited file it reads as-is (e.g. an e2e expected file the distro's OAP still meets) is None | Adapt the distro test or e2e case that reads it |
| Code of a storage other than BanyanDB, a cluster other than standalone / Kubernetes, a configuration other than ConfigMap or `none`, or `receiver-runtime-rule` (adding or removing such a provider: see the module row); other docs and tests; UI | None | — |

## When changing code

Two questions for every change: does it have a GraalVM distro impact ([Deciding the impact](#deciding-the-impact),
[Native-image concerns in any class](#native-image-concerns-in-any-class)), and if it does and the impact can't be
avoided, is the change prepared for it ([When the impact is inevitable](#when-the-impact-is-inevitable))?

The distro adapts to this repo, not the other way round: none of this is required, and none of it blocks a change.
These are preferences for when two ways cost the same; where a change doesn't follow one, the PR names it.

- **Replaced classes.** The distro's copy never sees an edit to one. Put new logic in a class it doesn't replace when
  that is natural; when you add a member other code calls, or change logic the copy duplicates, the PR names it.
- **`ModuleConfig` classes.** Prefer a class-level `@Setter` on a new config class. Upstream loads config by
  reflection and doesn't need it; the distro's generated loader does, and with it the distro only regenerates its
  loader instead of keeping a replacement copy.
- **DSL engines.** Prefer a generated class that is complete after its no-arg constructor. When instances need state
  set after construction (as TAL's function objects do), or the build-time API, the class naming or the rule data
  classes' setters change, name it. A new TAL function or input provider module has to join the distro's
  precompiler classpath.
- **Native-image concerns.** Before adding anything from
  [Native-image concerns in any class](#native-image-concerns-in-any-class), in any class, check whether the distro
  covers it; if not, the PR names it. Parsing YAML or JSON into maps and lists, instead of binding to classes, needs no
  metadata when it costs the same.
- **Run-time class generation.** Prefer not to add any outside the DSL compilers: Javassist, `LambdaMetafactory`,
  hidden classes, Guava `ClassPath` scans. The existing ones (`FieldsHelper`, `AnnotationScan`, `MeterSystem`) are why
  the distro replaces those classes. Ordinary lambdas and method references are fine; the native image compiles them.
- **Reflection and JSON.** Prefer a package the metadata scan covers for HTTP JSON body classes
  (`org.apache.skywalking.oap.query.<plugin>.entity`, `org.apache.skywalking.oap.server.admin.<module>.response`).
  A class instantiated by name, a new `LALOutputBuilder`, or `JsonFormat` on another protobuf type needs distro
  metadata: the PR names it.
- **Resources and config files.** Read runtime config through `ResourceUtils` from `config/`, as the other config
  files are. A new file there is a packaging entry and a resource glob in the distro, a new classpath resource read at
  run time a resource glob unless it is read with a constant name Native Image registers itself: the PR names it.
- **Modules and settings.** A new module or provider, a new or renamed `application.yml` key of a shipped module, and a
  new rule-selecting setting are always distro work.
- **Logging and TLS.** Don't assume Log4j2 runs on an `XmlConfiguration`, or that gRPC TLS runs on OpenSSL.

## When the impact is inevitable

Some changes can't avoid a native-image impact: a feature that needs run-time generation, reflection or discovery by
nature, a new module, an engine change. That is fine. The change then prepares for the distro's adaptation instead
of avoiding it:

1. **Name the mechanism** the distro will need in the PR item: build-time generation, a replacement of `<class>`, a
   reimplementation in the distro, metadata, packaging, or leaving the feature out
   ([GraalVM limits](#graalvm-limits-and-how-the-distro-deals-with-them)).
2. **Shape the code so that mechanism is cheap:**
   - **For a replacement,** keep the native-unfriendly part (reflection, generation, scanning, file discovery, native
     code) in one small class with a narrow, stable API, so the distro copies that class and not a large one, as it
     does with `FieldsHelper`, `ResourceUtils` and `VirtualThreads`.
   - **For build-time generation,** give the generator a build-time entry (an output directory, deterministic class
     names, source references), and keep the generated classes complete after their no-arg constructor, as the OAL,
     MAL, LAL and Hierarchy compilers do; what TAL sets on its classes afterwards (function objects) is what the distro
     has to replay.
   - **For a reimplementation,** put the part behind a seam (an interface, an SPI, a module provider) the distro can
     plug its own implementation into, as `graalvm-unsupported-admin` stands in for runtime-rule.
   - **For leaving it out,** put an optional feature that can't work natively in its own module or provider, so
     skipping it takes nothing else with it.
   - **For metadata and packaging,** keep reflected classes, JSON bodies and resources in a few predictable places,
     so one scan rule or resource glob covers them.
3. **The adaptation itself lands in skywalking-graalvm-distro** (the replacement, the reimplementation, the metadata),
   at its next sync or in a distro PR. This repo gets no distro-specific code; the seams and build-time entries above
   are ordinary APIs.

## When reviewing a change

1. The PR's "GraalVM distro impact" item has exactly one box checked; an "Impacted" one names the class, engine,
   setting or file.
2. Compare it with the diff, from the repo root, against the distro clone the user confirmed, fetched
   ([Keep a current distro clone](#keep-a-current-distro-clone)); without one, check the diff against this skill's
   snapshot (the replaced-class table) and say so. The classes the latest distro replaces, the classes
   this change deletes or renames that the distro still names (in its metadata, code or tests), the DSL engines and the
   starter:

   ```bash
   cd "$(git rev-parse --show-toplevel)"
   D=<the clone the user confirmed>
   comm -12 <(git diff --name-only <base>...HEAD | sed -n 's#.*/src/main/java/##p' | sort -u) \
            <(git -C "$D" ls-tree -r --name-only origin/main -- oap-libs-for-graalvm oap-graalvm-server build-tools/precompiler | sed -n 's#.*/src/main/java/##p' | sort -u)
   git diff --name-status -M <base>...HEAD -- '*/src/main/java/*.java' | awk '$1 ~ /^(D|R)/ {print $2}' | sed -n 's#.*/src/main/java/##; s#\.java$##; s#/#.#gp' | while read -r c; do git -C "$D" grep -l -F "$c" origin/main -- oap-graalvm-native/src/main/resources oap-graalvm-server/src build-tools oap-libs-for-graalvm; done
   git diff --name-only <base>...HEAD | grep -E '^oap-server/(oal-rt|oal-grammar|analyzer/(meter|log|trace)-analyzer|analyzer/hierarchy)/src/main/|server-core/src/main/java/.*/core/(analysis/meter|dsl)/|^oap-server/server-starter/(pom\.xml|src/main/resources/)|^dist-material/((log4j2\.xml|alarm-settings\.yml)$|release-docs/)'
   ```

   Module, provider and config classes among the changed files, and native-image hazards on added lines:

   ```bash
   cd "$(git rev-parse --show-toplevel)"
   git diff --name-only --diff-filter=d <base>...HEAD -- '*/src/main/java/*.java' | while read -r f; do grep -l -E 'extends (ModuleDefine|ModuleProvider|ModuleConfig|AbstractConfigurationProvider)\b' "$f"; done
   git diff --diff-filter=D <base>...HEAD -- '*/src/main/java/*.java' | grep -E '^-.*extends (ModuleDefine|ModuleProvider|ModuleConfig|AbstractConfigurationProvider)\b'
   git diff -U0 <base>...HEAD -- '*/src/main/java/*.java' | grep -E '^\+[^+]' | grep -E 'ClassPool|CtClass|LambdaMetafactory|Class\.forName|getDeclared(Field|Method|Constructor)s?\(|setAccessible\(|ClassPath\.from\(|JsonFormat\.(printer|parser)|@ProducesJson|@ConsumesJson|HttpResponse\.ofJson|\.toJson\(|@(ExceptionHandler|RequestConverter|Decorator)\(|getResource(AsStream)?\(|ResourceUtils\.|defineHiddenClass|(getMethod|getConstructor)\([^)]|\.fromJson\(|ObjectMapper|\.loadAs\(|\.(readValue|writeValue[A-Za-z]*|convertValue)\(|new Constructor\(|Proxy\.newProxyInstance|Object(Input|Output)Stream|System\.load(Library)?\(|getProtectionDomain|getCodeSource|ManagementFactory|ScriptEngine|ToolProvider|ResourceBundle\.getBundle|MethodHandles\.(lookup|privateLookupIn)|void finalize\(|Security\.(addProvider|insertProviderAt)|KeyStore\.getInstance|sun\.misc\.Unsafe|allocateInstance|@RequestObject|WorkPath\.|getResources\(|get(Fields|Methods|Constructors)\('
   ```

   These are heuristics: they miss indirect or multi-line inheritance, nested config classes and other spellings, and a hit is a question,
   not a verdict (reflection inside the build-time DSL compilers, or a resource the distro already embeds, has no
   impact). Judge each with [Deciding the impact](#deciding-the-impact). "None" with hits needs
   a reason; a hit the item doesn't mention is a review comment.
3. For an edited replaced class, read the distro's copy (`git -C "$D" show origin/main:<path>`) and see whether the change adds or
   alters a member callers use, or logic the copy duplicates. The `javap` check in
   [Checking a branch against the distro](#checking-a-branch-against-the-distro) is the optional mechanical version.
4. Only what this change introduces counts ([Scope](#graalvm-distro-impact)): an impact already on master, or already
   reported to the distro's maintainers, is no finding against this change.
5. Distro impact is never a reason to reject or delay a change. A missing or wrong item is.

## Checking a branch against the distro

Optional: the PR item needs no build. This is for when you want certainty, or are working on the distro side. It
needs a separate clone, since it checks your branch out in its `skywalking/` submodule: ask the user for one, or for
the go-ahead and a folder to clone into, as [Keep a current distro clone](#keep-a-current-distro-clone) says.

```bash
cd <the separate clone>
git submodule update --init
git -C skywalking fetch <your-fork-url> <branch> && git -C skywalking checkout FETCH_HEAD
make init-skywalking compile test M2_REPO=/tmp/distro-m2   # needs a GraalVM 25.5 JDK (Makefile: GRAAL_HOME)
```

The distro pins an older upstream commit, so failures from commits between its pin and your base show up too: run
it on your base as well and compare. What a failure means:

- Compile or precompiler failure: usually the build-time API or a rule's compilation broke. Impacted.
- `ReplacementClassStalenessTest`: a replaced class's file changed. It can't tell a breaking change from a harmless
  one; diff the member list (below).
- `PrecompiledYamlStalenessTest`, `ImmigrationScopeTest`: a bundled rule file, or a provider or rule file set,
  changed. Usually mechanical.
- A MAL / LAL / TAL / Hierarchy comparison test or `RegistryParityTest`: the precompiled output differs from a fresh
  compile, or the registry from upstream's loaders. Impacted. A "both paths returned EMPTY" failure in a hand-built-input
  test (`OapTest`-style) usually means its input doesn't cover a new rule (the message says "likely"); check that the
  two paths don't fail the same way.

Its tests also check the module selectors, the provider set on the classpath and the inventories. Passing is still
not proof: the JVM tests don't boot the OAP, so a member missing on a replaced class, or native metadata no test
covers, can pass them and show up only when the native image is built (`make native-image M2_REPO=/tmp/distro-m2`,
or `make native-image-macos M2_REPO=/tmp/distro-m2` on macOS) and booted, or in the distro's e2e cases.

Member check for a replaced class (upstream jar from `make init-skywalking`, the distro's from `make compile`):

```bash
members() { javap -p -cp "$1" "$2" | grep -E '^\s' | grep -v -E '^\s*private |static \{\}' | sort -u; }
comm -23 <(members <upstream-jar> <FQCN>) <(members <distro-for-graalvm-jar> <FQCN>)
```

A screening tool: each line is an upstream member the copy lacks textually (modifiers, generics and `throws` count
too; inherited members need a look). Some are dropped on purpose (run-time Javassist and runtime-rule members such as
`MeterSystem#removeMetric`); a new one that code calls is a break. For the four classes in `oap-graalvm-server`,
compare with its jar instead of a `*-for-graalvm` one.

## What the PR states

Check exactly one box in the template's "GraalVM distro impact" item, for what this change introduces only. "None"
when nothing in [Deciding the impact](#deciding-the-impact) applies to its diff. For "Impacted", keep the ways that apply and name what the
distro's next sync has to adapt: the class and member, the engine behavior, the setting, the file, and, for an
impact the change can't avoid, the mechanism the distro will need
([When the impact is inevitable](#when-the-impact-is-inevitable)). One line per way, e.g.:

- `a class the distro replaces. Adds getOtlpLogSearchableTags() to OtelMetricReceiverConfig, called in
  OtelMetricReceiverProvider#start().`
- `bundled rule files. Adds otel-rules/foo/bar.yaml.`
- `a native-image concern. FooHandler returns FooResponse as JSON from a package the metadata scan doesn't cover;
  the distro needs a reflection entry for it.`
- `a native-image concern, inevitable. BarLoader discovers plugins by scanning the classpath; the scan is isolated in
  BarPluginScanner, which the distro replaces with a build-time manifest.`
- `a DSL engine. Generated TAL classes get their function objects set after newInstance().`
