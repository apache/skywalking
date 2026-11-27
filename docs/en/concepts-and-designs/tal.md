# Trace Analysis Language

Trace Analysis Language (TAL) in SkyWalking is a Domain-Specific Language (DSL) to analyze spans. A TAL rule reads one
span at a time and decides what it is, which service, instance and endpoint it belongs to, and which metrics and
records it produces. A metric a rule declares goes through the meter system, so it is stored, downsampled, queried and
alerted on like every meter metric; a rule over SkyWalking agents' segments also hands the OAL engine the sources the
`.oal` files compute from.

A rule reads one input, named by its `inputType`. This page describes the language every input shares; each input has
a page of its own for what only it reads and builds:

| | `otlp` | `sw-native` |
|---|---|---|
| Sent by | OpenTelemetry SDKs and collectors | SkyWalking agents, as trace segments over gRPC, HTTP or Kafka |
| Rule files | `talFiles` (`SW_TRACE_TAL_FILES`) | `nativeTalFiles` (`SW_TRACE_NATIVE_TAL_FILES`) |
| Which rules see a span | the rules declared for the span's `service.layer` | every rule of every listed file whose `requiredModules` are loaded, in list order, on every span of the segment, in wire order |
| What a rule reads | `span`, the OTLP `Span`, and `meta`, what the receiver derived from it | `span`, the native `SpanObject`, `segment()`, its `SegmentObject`, and the values the runner derives from the segment |
| Runs | one per span and rule | `first {}` on a span `0`, the span's run, and `build {}` after the segment's last span; `segmentDef` lives for the segment |
| Outputs | `metrics {}` and `source {}`, each sent at its statement; `persist {}` and `tag` for the stored span | inside `build {}`: `metrics {}` and `source {}` |
| The trace itself | stored by the receiver when no span listener vetoes it and no rule's `persist {}` votes to drop it, then past the receiver's rate limit and head sampling. An `enforcer {}` skips head sampling only; with `otlpTraceStorage: zipkin` it marks the span `debug`, which the Zipkin path keeps past both its rate limit and its sample rate. Neither overrides a veto | stored as the `native-segment` rule's `Segment` source when the segment is sampled, the segment as received |
| Network alias | learned from caller and callee pairing, `prepareNetworkAlias` and `buildNetworkAlias` | written by the callee alone, `writeNetworkAlias`, from the address the `sw8` header carries |
| Entity names | in `metrics {}`, formatted by the entity constructor, `service(...)`, `endpoint(...)` and the others, where it runs, inline in `scope` or in a `def`; for a `source {}`, by the rule, through `formatServiceName`, `formatInstanceName` and `formatEndpointName` | formatted by the rule, through `formatServiceName`, `formatInstanceName` and `formatEndpointName`, before `source {}` |
| Retries | with `dedupWindow` above `0` (off by default), a span seen again on the same node within the window, while its id is still held, is analyzed once | none |
| A rule that throws | skipped for that span, logged, and the span's other rules still run | stops the segment there; nothing is caught |
| Its page | [TAL for OpenTelemetry Spans](tal-otlp.md) | [TAL for Native Segments](tal-sw-native.md) |

The TAL config files are in YAML format, and are located under directory `tal`. The `trace-analyzer` module lists the
files for OpenTelemetry spans in `trace-analyzer/default/talFiles` and those for SkyWalking agents' segments in
`trace-analyzer/default/nativeTalFiles`, in the `application.yml` file or through the environment variables above. The
files are read when the OAP starts. A listed name reads its file, `<name>.yaml` by convention; beside another file of
that name, such as an editor's `<name>.yaml~`, its `.yaml` or `.yml` file is read and the others are logged, and two
candidates otherwise, such as `<name>.yaml` and `<name>.yml`, fail the boot; a rule names the file it came from as it is
named. Every listed file of both lists, the native one when the native rules run and a file whose `requiredModules` are
loaded, is read, its `layerDefinitions` registered, a file of definitions alone included, before a rule of either list
compiles, so a rule may name a layer a file of the other list declares. See [OpenTelemetry Trace
Analysis](../setup/backend/trace-analyzer.md) and [Native Trace Analysis](../setup/backend/native-trace-analysis.md) for
the module's settings and for what the shipped rule files produce.

Anyone who has written a [LAL](lal.md) rule can read a TAL rule: the grammar is LAL's, with the log-specific parts
removed and span-specific parts added. The differences are listed at the end of this page.

## Rule file

```yaml
# tal/<file>.yaml
metricPrefix: otlp     # every metric declared in this file is stored as <metricPrefix>_<name>
requiredModules:       # optional; the file's rules are compiled and run only when every module listed is loaded
  - gen-ai-analyzer
layerDefinitions:      # optional, the same contract as LAL and MAL: registered before the rules compile
  - name: IOT_FLEET
    ordinal: 1000
    normal: true
rules:
  - name: otlp-general # unique within its layer; a native rule's name is unique among the native rules
    layer: OTLP_GENERAL # a registered layer name
    inputType: otlp    # otlp, the default, or sw-native
    dsl: |
      filter { ... }
```

A rule names exactly one `inputType`. The OAP refuses to start on a rule whose `layer` is not registered, naming the
rule and the layer; declare the layer under `layerDefinitions` or use a built-in one. It also refuses two rules of one
name in one layer, and two native rules of one name, across the listed files, naming both files.

`requiredModules` names the modules a file's rules need, such as a module whose functions or sources they use.
A file whose modules are not all loaded is not compiled, and so not checked, and its rules do not run. Every module a
listed file names is a required module of `trace-analyzer` whenever it is loaded, so it starts before the rules are
compiled.

## Filter

```text
filter {
  <def / assignment / if / foreach / abort / log>*
  extractor { ... }
  <the input's own blocks>   // persist {} on otlp, first {} on sw-native
}
```

### Statements

These statements are allowed in `filter` and `extractor`, and in the native input's `first {}` and `build {}`. The
OTLP `persist {}` holds statements of its own, listed on the OTLP page:

```text
def <name> = <expr> [as <Type>]            // declare a variable
<name> = <expr> [as <Type>]                // assign a declared variable
if (<condition>) { ... } else if (<condition>) { ... } else { ... }
foreach (<name> in <list>) { ... }
abort {}                                   // stop this run of the rule
log <trace | debug | info | warn | error> "<message>" [, <expr>, ...]
```

- **`def` declares and `=` assigns.** `=` assigns a variable a `def` declared earlier in the rule's text and in scope
  where the assignment stands, or a `segmentDef` of a native rule. A name nothing declares is a compile error, and so
  is a value that does not fit the variable's type, the type of the value its declaration gave it. A later `def` of a
  declared name assigns it too.
- **Declaring before the branches.** A variable the branches of an `if` assign is declared before them. A `String`, a
  `Layer` or an enum is declared with a typed `null`, `def owner = null as String`, `def callerLayer = null as Layer`;
  it stays `null` on the paths that do not assign it. A number or a boolean is declared with a literal, `def
  logicLatency = 0`, or with a typed `null`, `def latency = null as Integer`, since a cast keeps a `null`.
- **Scopes.** A filter-level `def` is in scope inside the `extractor`. A `def` in a `foreach` body, in a native rule's
  `build {}` block or in its `first {}` run declares a variable of that iteration, block or run; `=` assigns it there,
  or assigns a variable of an enclosing scope. A `def` first given a value inside an `if` branch is `null` after it on
  the paths that did not give it one.
- **`foreach`.** `<list>` is a `java.util.List`, such as the native `span.refsList` or a list a function returns. The loop
  variable is typed to the list's element type when the rule compiles, so `ref.parentService` is a compile-checked
  chain. A `def` inside the body is declared again at each iteration and is not visible after the loop; a variable
  declared before the loop and assigned inside it with `=` keeps its value across iterations, so the body compiles for
  what any iteration can start with: a `put` on a map the rule made, or a quotient narrowing into a whole number, is a
  compile error when an earlier iteration may have given the variable another value. `foreach` is allowed
  wherever `if` is. A list or an array also reads an element by its index, `span.refsList[0]`.
- **`abort {}`** is allowed anywhere, and stops the whole run of the rule, the statements after the block it sits in
  included. A statement after it in the same block could never run and is a compile error.
- **`log`** writes the message through SLF4J under the logger `org.apache.skywalking.trace.analysis.script.<script>`,
  the script being the rule file without its extension, such as
  `org.apache.skywalking.trace.analysis.script.native-rpc`. Each `{}` is replaced by the next argument as SLF4J formats
  it, and the arguments are evaluated only when the level is enabled. An argument beyond the placeholders is a compile
  error, as SLF4J would drop it, but for one last argument that can be a `Throwable`, which SLF4J logs as the exception;
  one whose type is known only when the rule runs, such as a map's value, fails the rule when it is neither a
  `Throwable` nor null there, a null logging no exception. A placeholder no argument fills prints as it is. A `{}` after an odd run of backslashes, `\{}`, is a literal
  `{}`. A function that logs, such as `parseStatusCode`, logs under the calling rule's script logger; a function that
  calls other Java, such as the native naming functions, logs as that Java logs, under its own class name.

Statements may be separated by `;` as well as by newlines.

### Expressions

An expression starts with `span`, an input's own root such as the OTLP `meta`, a variable, a function or a literal;
any other first name, such as a misspelled `mta.kind`, is a compile error. `span` is typed to the input's span when the
rule compiles, so every getter chain after it is checked then. A string literal is plain text in `"` or `'`: a
backslash escapes only the quote, `'` and itself, so `"a\"b"` is the three characters `a"b` and `"C:\temp"` keeps its
backslash, and there is no `\n` or `\t`. `${...}` is text, except in a `rateLimit` id, where `\${` keeps it text.
Numbers are Java's, `1`, `10L`, `1.5`; and `true`, `false` and `null` are
literals. An integer written with a leading 0 is octal, as in Groovy, so `010` is 8 in a value, a comparison, an
argument, `buckets`, `ranks`, `labelLimit`, `rpm` and an `[index]` alike, and `08` is a compile error; a leading 0
before a fraction, an exponent or an `F` / `D` suffix, `00.5` or `010F`, is decimal, as in Java. A numeric field,
such as the OTLP `meta.latency`, is a number, so `meta.latency + 1` adds. A method after a numeric field,
`meta.latency.doubleValue()`, is the boxed number's. A method's overload is chosen as LAL chooses it,
when the rule compiles: of the methods of the name and the arguments' count, the one the arguments fit best, an
argument of the parameter's own type before one widened or boxed to it, and that before a value whose type is known
only when the rule runs, such as a map's value, converted or cast then; the most specific of several that fit alike.
No string is read as a number nor a number as a string, so `span.name.concat(42)` and `substring(attr("n"))` are
compile errors; write `concat("42")` or `substring(attr("n") as Integer)`. A value of unknown type that two overloads
take, `span.name.indexOf(m.get("d"))`, is a compile error asking for `as String` or `as Integer`. A literal takes no method, field or index, so `"x".trim()` is
a compile error; `("x").trim()`, or a `def` holding the literal, calls it.

#### What a rule can call

A rule calls the functions its input and the loaded modules provide, reads a field of the input, of `meta` or of an
object a function returns by its getter (`span.name`, `ref.parentEndpoint`, or written as `getX()`, `isX()`,
`hasX()` or `containsX(key)`), and calls these Java methods, read as a property too (`span.name.length` calls `length()`); the String
methods are the ones LAL calls:

- **String:** `length`, `isEmpty`, `trim`, `toLowerCase`, `toUpperCase`, `startsWith`, `endsWith`, `contains`,
  `indexOf`, `lastIndexOf`, `substring`, `replace`, `replaceAll`, `replaceFirst`, `split`, `charAt`, `equals`,
  `equalsIgnoreCase`, `matches`, `concat`, `toString`.
- **Numbers:** `intValue`, `longValue`, `doubleValue`, `floatValue`, `toString`, and `toPlainString` on a decimal. There
  is no `Math`: arithmetic uses the operators and the `round` and `floorMod` functions.
- **JSON**, from `parseJsonObject`: on an element, a primitive included, `getAsString`, `getAsInt`, `getAsLong`,
  `getAsShort`, `getAsByte`, `getAsDouble`, `getAsFloat`, `getAsBoolean`, `getAsCharacter`, `getAsNumber`,
  `getAsBigDecimal`, `getAsBigInteger`, `getAsJsonObject`, `getAsJsonArray`, `getAsJsonPrimitive`, `getAsJsonNull`,
  `isJsonNull`, `isJsonObject`, `isJsonArray` and `isJsonPrimitive`; on an object, these and `get`, `has`, `size`, `keySet`, and `getAsJsonObject`,
  `getAsJsonArray` and `getAsJsonPrimitive` of a member; on an array, these and `get`, `size` and `isEmpty`.
- **Maps:** `get`, `getOrDefault`, `containsKey`, `isEmpty`, `size`, `keySet`, and `put`. **Lists and sets**, a map's
  `keySet()` included: `size`, `isEmpty`, `contains`, `add`, and `get` on a list. `put` and `add` change only a map or
  list the rule made with `emptyMap()` or `emptyList()` and holds in a `def`, as every path to the call leaves it: a
  `def` assigned one, grouped or from another `def` holding one, or null, also through a `foreach` whose body assigns
  it one again.
- **Layer and enums:** `name`, and `isNormal` on a layer. **Any value:** `toString` and `equals`, the only methods a
  value whose type is known only when the rule runs calls. A value's methods are its class's and its supertypes', so
  a `BigDecimal` calls the numbers' methods too.

Any other method is a compile error naming the methods its value has, and so is a static method called through a value
(`amount.valueOf(n)`), a variable-length one (`format`), `getClass()`, and a method of an input's object other than its
getters (`getX()`, `isX()`, `hasX()` and `containsX(key)` with an uppercase letter after the prefix, so `hashCode()`
is none). A
primitive result takes its box's methods: `round(x).toString()`.

This is the set this release supports, and it grows as rules need more: propose an addition in an issue or a pull
request.

TAL evaluates values as [LAL](lal.md) does, except where noted here:

- A bare condition, `if (x)`, uses Groovy truth: `null`, `false`, `0`, `NaN`, an empty string and an empty collection,
  map or array are false. `as Boolean` converts as LAL's: a boolean, the text `true` or `false` in any case, and a
  number or a text holding one by zero (`0` is false, any other number true); any other value, such as `"yes"`, fails
  the span.
- `+` joins as soon as one side is a string, a missing boxed number joining as `"null"`: a string joins onto a
  string, a number, a character or `null` on its left, and any value but an array, a collection or a map on its right
  (`"x" + true`); any other value on the left (a boolean, an enum, a JSON value, any other object), and an array, a
  collection or a map on either side, fails, the compile where its type is known, else the span (`true + "x"`,
  `span.name.split(",") + "x"`, `"x" + span.refsList`), and so does a boolean beside a number. Where
  neither side is a string nor both are numbers when the rule compiles, such as a map's value beside a number, numbers
  add (`Integer + Long` is a `Long`, a `BigDecimal` stays exact, a `BigInteger` beside a whole number stays whole, a
  float or a double makes a `Double`) and a string, a number, a character or a `null` on the left joins, two characters
  included (`("/cart").charAt(1) + ("/cart").charAt(2)` is
  `"ca"`, as TAL always joined them). A float computes in double, even with another float. `-`, `*` and `/` take numbers only, so a
  string is never multiplied.
- `/` on two whole numbers is the exact decimal, as Groovy divides: `7 / 2` is `3.5`, and one that does not terminate
  is rounded half up to the larger of its operands' scales and ten decimals (`7 / 3` is `2.3333333333`). It stays that decimal until where it goes converts it:
  an int or long target, a setter's or a function's parameter, a metric's value, an `as Integer` or `as Long` cast or a
  `def` that holds a whole number, truncates it (only a quotient narrows into such a `def`), so `span.startTimeUnixNano / 1000000` is the whole milliseconds; a
  double target reads its double value; text its digits; and a comparison its exact value. `+`, `-`, `*` and `/` beside it
  compute exactly, or in double beside a float or a double. A Java method takes it as a decimal, so `substring((5 / 2) as
  Integer)` converts it, and a number typed only as `java.lang.Number` divides beside a float or a double, and else once
  converted `as Long` or `as Double`. A division in double divides as Java does, `1.0 / 0` giving an infinity and `0 /
  0.0` a NaN; any other zero divisor fails the span.
- A character is the one-character string it is. A string or a character against a number reads as the number it
  reads as, trimmed: `"3" < 5` and `"3" == 3` are true, and `span.name.charAt(0) == 1` is true for `"1x"`. In an
  ordering, `<`, `>`, `<=` or `>=`, one that reads as no number fails the span, or the compile for a literal; in an
  equality it is unequal. Two strings order as numbers when both read as one (`"10" > "2"`), else as text, and equal
  as text.
- Numbers compare as Java compares them, as TAL always did: in double when either is a float or a double, a NaN
  comparing false in every comparison but `!=` and -0.0 equal to 0, else exactly. A null equals only a null and orders
  below every value.
- A cast keeps a null: `labels().get("missing") as Integer` is null. A blank text casts to null too in a numeric cast and in
  `as Boolean`, and a bare `if (x as Boolean)` reads it as false, while `as String` keeps the text as it is: `attr()` reads an absent attribute as `""`, and this keeps
  it the missing value LAL's `null` is. A number orders above a `null`. A value that does not convert fails the span, as LAL fails the log: `"abc" as Integer`,
  `"1.5" as Long`, a boolean, a map or a character as a number, a class-name `as` on a value of another class, and
  `as Layer` or `as <Enum>` on a name that is none. A null fails in arithmetic and as a primitive argument. A
  numeric cast, and a primitive argument it converts, trims a text first, as LAL and Groovy do: `" 42 " as Long` is
  42, where the Java analyzer that native trace analysis replaced failed the segment on a padded
  `transmission.latency` tag. So the
  shipped `otlp-general` rule's `attr("http.response.status_code", "http.status_code") as Double >= 400` fails a span
  whose status code is text that is no number, and does not take the error branch for `NaN` (a NaN compares false);
  an absent code is null, below 400.

`as` converts to `String`, `Long`, `Integer`, `Double`, `Float` or `Boolean`, to `Layer` or one of the enums listed
under [Typed Layer and enum values](#typed-layer-and-enum-values), or, as a Java cast, to a class named by its full
name, such as `java.util.List`, `java.lang.Long` or the member class `io.opentelemetry.proto.trace.v1.Span.Event`;
any other name, such as `as Bool`, is a compile error; `as boolean` reads as `as Boolean`. A class-name `as` that no
value of the expression's type can pass, `"7" as java.lang.Long`, is a compile error, and so is an argument of such a
type for a method's parameter, `span.name.contains(42)`.

A comparison is decided by the types of its two sides when the rule compiles; a string or a character beside a
number reads as the number it is, and `as T` converts anything else:

| Left and right | Allowed | Compared as |
|---|---|---|
| number and number | every operator | numbers, so `110 == 110.0` is true and an `int` compares with a `long`; as Java's primitives (in double beside a float or a double, a NaN false but in `!=`, -0.0 equal to 0), else exactly; a null below every value |
| boolean and boolean, String and String, two values of one type | `==`, `!=` | equal values |
| two lists, arrays, sets or maps, of one kind or not (a list against an array) | `==`, `!=` | by content, as every DSL compares them: lists and arrays element by element in order (a list against an array too), sets by their elements in any order, and maps by their keys and each key's value, each element as a value; a JSON value compares as what it holds (an array as a list, an object as a map, a primitive as its value) |
| a value that can be absent and `null` | `==`, `!=` | a null check |
| a `Layer` or an enum value and a string literal | `==`, `!=` | by name; the literal is checked when the rule compiles |
| a value whose type is known only when the rule runs, such as the OTLP `event(...)`, and a string literal | `==`, `!=` | equal values; an event never equals a string, so compare `event(...)` with `null` |
| a string, a character or a value whose type is known only when the rule runs, and a number | every operator | as the number the string reads as; in an ordering one that reads as none fails the span, in an equality it is unequal |
| a string, a character or a value whose type is known only when the rule runs, and a string | `<`, `>`, `<=`, `>=` | as numbers when both read as one, else as text |
| anything else, such as `meta.root == 1` | | a compile error naming both types |

A function that returns a String may also be ordered as a number with `as`: `attr("http.response.status_code") as
Integer >= 400`. A method can
follow a function that returns a string, `attr("x").trim()`. A comparison compares
two values, so `(a == b) == true` is a compile error. `!` binds tighter than `&&` and `||`, as in Java.

#### Typed `Layer` and enum values

`as` converts a string to a `Layer`, or to an enum a source takes, such as `RequestType`, `DetectPoint`,
`MQOperation`, `VirtualCacheOperation`, `TagType` and `GenAITraceRefType`:

```text
def calleeLayer = "VIRTUAL_CACHE" as Layer
def type = "HTTP" as RequestType
```

`as Layer` looks the name up among the registered layers and fails on an unknown one. A literal, a string, a number or `true` / `false`, is checked when the rule compiles,
against the layers the listed files register, their `layerDefinitions` included. An enum literal is checked against
the enum's constants. A `def` given a typed value has that type, so a misspelt name is a compile error where it is
written, not a failure when its branch runs. `as Layer` and `as <Enum>` keep a literal `null` and give it the type.

### Extractor

```text
extractor {
  <def / assignment / if / foreach / abort / log>
  metrics { ... }                           // see below; inside build {} on sw-native
  source <Name> { ... }                     // see below; inside build {} on sw-native
  <the input's own statements and blocks>   // see the input's page
}
```

An `extractor` may sit inside a filter-level `if`, and in a native rule's `first {}`, but not inside another
`extractor` or an OTLP `persist {}`.

## Functions for every input

These functions are callable wherever a value goes, in a rule of any input. An input adds functions of its own, which
its page lists, and a module can add more, see [Extension points](#extension-points).

| Function | Returns |
|---|---|
| `formatServiceName(name)` | the service name after the core's length limit; `null` stays `null`; a truncation logs |
| `formatInstanceName(name)` | the instance name after the length limit; a truncation logs |
| `formatEndpointName(service, endpoint)` | the endpoint unchanged when either argument is empty; otherwise the name after the length limit and the [endpoint grouping](../setup/backend/endpoint-grouping-rules.md), which keeps per-service state |
| `buildServiceId(name, normal)` | the service id, as `IDManager.ServiceID.buildId` builds it |
| `matchGenAIModel(model)` | the match for the model's longest prefix in `gen-ai-config.yml`: `provider`, `unknown` when none or when the model is empty, and `pricing`, the matched model's `inputEstimatedCostPerM` and `outputEstimatedCostPerM`, `null` when no model matches |
| `evaluationActive()`, `evaluationSampleRate()` | contributed by `ai-evaluation`, in a file that lists it under `requiredModules`: whether the module evaluates, false when its judge configuration is incomplete, and its `sampleRate`, the traces sampled out of 1,000,000 |
| `buildInstanceId(serviceId, name)` | the instance id, as `IDManager.ServiceInstanceID.buildId` builds it |
| `buildEndpointId(serviceId, name)` | the endpoint id, as `IDManager.EndpointID.buildId` builds it |
| `minuteTimeBucket(millis)`, `recordTimeBucket(millis)` | the minute and the record (second) time bucket, in the JVM's time zone |
| `round(double)` | the `long` nearest the value |
| `cut(text, length)` | the text cut to at most `length` characters |
| `isBlank(text)` | whether the text is `null`, or empty once trimmed |
| `emptyList()`, `emptyMap()` | a new, empty, mutable list or map |
| `isEmpty(list)` | whether the list is `null` or holds nothing |
| `parseJsonObject(text)` | the text parsed as a JSON object, `null` for blank text; malformed or non-object text fails the rule. The getters that follow are Gson's, so their coercions and failures are Gson's |
| `parseLongOrZero(text)`, `parseIntOrZero(text)` | `0` for `null` or empty text; the number; or `0` after the warning `Failed to parse value to long: {}` (`to int` for the second) under the script's logger, for text that does not parse |
| `parseSecondsAsMillisOrZero(text)` | a number of seconds as the whole milliseconds nearest it, an `int`; `0` for `null` or empty text; or `0` after the warning `Failed to parse value to seconds: {}` under the script's logger, for text that does not parse, is negative or is more milliseconds than an `int` holds |
| `murmur3Hash(text)` | the first 64 bits of the murmur3-128 hash of the text's UTF-8 bytes, as a `long` |
| `floorMod(x, y)` | the floor modulus of two `long` values, never negative for a positive `y` |
| `parseStatusCode(text)` | the text as an `Integer`, or `null` after logging, under the script's logger, the warning `span {} has illegal status code {}` with the span and the text |
| `urlHost(url)` | the authority of a URL, `host` or `host:port`, without scheme, user info, path, query or fragment; empty when the value has none, which a value without a `scheme://` prefix, such as a bare path, does not |
| `toJson(expr)`, `toJsonArray(expr)` | the value as a JSON object or array, as in LAL |
| `component(name)` | the component id from `component-libraries.yml`, matched exactly and then ignoring case, so `grpc` finds `GRPC` and `postgresql` finds `PostgreSQL`; `0`, the `Unknown` component, when nothing matches |
| `isGateway(address)` | whether the address is listed in the agent analyzer's [uninstrumented gateways](../setup/backend/uninstrumented-gateways.md), matched exactly, a bare host also at port 80, the port the file gives an instance that names none; nothing is resolved, so every spelling and port the callers dial has to be listed. Always false when the `agent-analyzer` module is not loaded, and for an empty address. A change made through [dynamic configuration](../setup/backend/dynamic-config.md) applies to the next span |
| `isNoUpstreamRealAddressAgent(componentId)` | whether the agent analyzer's `noUpstreamRealAddressAgents` lists the component, the agents, such as Nginx and Envoy, that cannot see the real remote address; false when the `agent-analyzer` module is not loaded |

## `metrics`

A `metrics {}` block declares one metric and emits one value for this span. The name, the function and the entity
constructor are literals, so every metric a file declares is known when it compiles and its storage is created
then. In an `otlp` rule it sits in the `extractor`; in a `sw-native` rule, inside `build {}`.

```text
metrics {
  name "<metric>"                    // literal; stored as <metricPrefix>_<metric>
  scope <entity>                     // an entity constructor, or a `def` holding one
  function <function>                // literal, see the table
  value <expr> [as <Type>]           // the observation; a number, or a string that parses as one. Not used by percent
  [match <condition>]                // percent only, required: the span counts as a match when true
  [status <condition>]               // apdex only, required: the span counts as successful when true
  [labels k: v, ...]                 // required by the *Labeled functions, forbidden otherwise
  [labelLimit n]                     // *Labeled functions only; the label sets one row keeps per minute, 50 by default
  [buckets [n, n, ...]]              // percentile only, required; whole numbers, ascending, in the unit of `value`
  [ranks [50, 75, 90, 95, 99]]       // percentile only; whole numbers in 1..100, ascending; this is the default
  [timestamp <expr> [as <Type>]]     // milliseconds; default the span's start, as the agent path buckets a call
}
```

Each clause other than `labels` is written once, and each label key once; a repeated one is a compile error. A
numeric cast on `value` or `timestamp` converts before the number is taken, so `value attr("amount") as Double` reads
`"1.7"` as 1 rather than failing to parse it. Two blocks may feed one metric, from two branches of an `if`
for instance, when they agree on its function, scope, buckets, ranks and `labelLimit`, an omitted `labelLimit`
counting as its default of 50; any difference is a compile error. The same
holds across rules and files, two files sharing a `metricPrefix` included: blocks of one stored name that disagree
fail the rule that loads second, and so the boot. A label value is stored with `|` and `,` replaced by `_`, since those
two characters separate the stored labels.

Every label set of a labeled metric is an entry in one stored row per entity per minute, so its labels should take a
few values, as a status code or a method does, and never a URL, an id or a statement. A row keeps at most
`labelLimit` label sets, 50 by default as OAL's `labelCount` keeps; once it is full, a new label set is not counted,
while the label sets it already holds keep counting.

The entity constructors, usable inline in `scope` or in a `def`. Every name passes through the core module's naming
rules, as a meter entity's does:

| Constructor | Entity |
|---|---|
| `service(name, layer)` | a service in the layer |
| `instance(service, instance, layer)` | a service instance |
| `endpoint(service, endpoint, layer)` | an endpoint |
| `serviceRelation(CLIENT \| SERVER, source, sourceLayer, dest, destLayer, componentId)` | a call between two services, each in its own layer; `componentId` is an `int`, normally `component(...)` |
| `serviceInstanceRelation(CLIENT \| SERVER, sourceService, sourceInstance, sourceLayer, destService, destInstance, destLayer, componentId)` | a call between two instances |

A `layer` argument is a string expression. A name the rule spells out in literals, in parentheses or joined with `+`,
such as `"TEAM_" + 2` or a number, is checked when the rule compiles, against the layers the listed files register,
their `layerDefinitions` included, and one that is not registered is a compile error. A name computed when the rule runs
that is not registered skips the block, with one warning per name; a `value` that does not parse as a number, or any
other failure inside a block, skips that block alone, with one warning per metric, and the rule's later blocks still
run. The value is read and the metric built before the block registers its entity, so a skipped block, one whose metric
the meter system could not create included, writes no traffic. An entity held in a `def` has one shape: reassigning it
with a different constructor is a compile error. An entity is used as its constructor builds it: a method, a field or an
`as` after the constructor, or after the `def` that `scope` names, is a compile error.

The functions are the meter system's, named for what they do to the values of one minute:

| `function` | Value | Meaning per minute per entity |
|---|---|---|
| `cpm` | number | the total of the values divided by the bucket's length in minutes, so hour and day rows read as a rate; `value 1` is calls per minute. The same number `core.oal`'s `cpm` computes; `sumPerMin` is accepted as an alias |
| `sum` | number | the raw total; at hour and day level the totals add rather than average |
| `avg` | number | the mean; `value meta.latency` is mean latency |
| `max`, `min`, `latest` | number | |
| `percent` | `match` condition | `matches * 10000 / total`, the `core.oal` `percent` scale; `match meta.statusCode != "ERROR"` is the success rate |
| `apdex` | `value` and `status` | the `core.oal` `apdex`: thresholds from `service-apdex-threshold.yml` looked up by the entity's service name as stored, after the service name length limit, satisfied under `T`, tolerated under `4T`; a relation scope is a compile error |
| `sumLabeled`, `avgLabeled`, `maxLabeled`, `minLabeled`, `latestLabeled`, `sumPerMinLabeled` | number, by `labels` | one series per label set; `labels status: attr("http.response.status_code", "http.status_code")` splits a count by status code |
| `percentile` | number, by `buckets` | the observation counted in the first bucket whose bound is not below it, so each bound is its bucket's upper edge, as MAL keys OTel's `le`; a value above the last bound counts in an overflow bucket keyed `9223372036854775807` (`Long.MAX_VALUE`), the key MAL gives OTel's `+Inf` bucket. The buckets are reduced to the `ranks` when the minute is persisted; a rank reads as the upper edge of the bucket it falls in, and a rank that falls in the overflow bucket reads the last bound, so a rank that reads the last bound may be above it, and an alarm condition that needs a value above the last bound never fires |

With `cpm`, `percent` and `apdex` a TAL metric named like an agent metric holds the same number in the same unit, so
an alarm rule or a dashboard widget written for `service_sla` ports to `otlp_service_sla` by renaming the metric.

## Source (existing system adoption)

`source {}` hands one source of the existing system to the source receiver, which dispatches it by its scope: to the
OAL engine, as the agent analysis builds it for `core.oal` and the other `.oal` files, or to the dispatcher a module
declares for it. It exists so that an analysis can move to TAL without changing what the existing system computes
from it: every metric named in `.oal` keeps its name, its function and its number, and a record such as
`DatabaseSlowStatement` is stored as before. A new metric is declared with [`metrics {}`](#metrics) instead.

```text
source <Name> {
  <field> <expr> [as <Type>]
  ...
}
```

- **`<Name>`** is the name OAL's `from()` gives the source, the `name` of its `@ScopeDeclaration`: `Service`,
  `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`, `ServiceMeta`,
  `EndpointMeta`, `DatabaseAccess`, `DatabaseSlowStatement`, `ServiceDatabaseSlowStatement`, `CacheAccess`,
  `VirtualCacheSlowAccess`, `MQAccess`, `MQEndpointAccess`, `Segment`, `TagAutocomplete`, `GenAIProviderAccess`,
  `GenAIModelAccess`, `GenAIEvaluationSample`, and any source a module declares. `GenAIEvaluationSample` is no OAL
  source: `ai-evaluation`'s dispatcher queues the span it describes for the module's LLM judge and stores nothing.
  Only a concrete source class with a public no-argument constructor qualifies; an unknown name is a compile error.
- **Each field statement** calls the source's setter of that name, checked when the rule compiles. Keywords such as
  `name`, `status`, `layer` and `endpoint` work as field names here, and set the source's field. The value is
  converted to the setter's parameter type:
  - a number, a boolean, a string or a byte array as it is;
  - a `Layer` or an enum, such as `RequestType` or `DetectPoint`, as it is when the value already has that type (see
    [Typed Layer and enum values](tal.md#typed-layer-and-enum-values)), and from its name when it is a string. A
    literal name is checked when the rule compiles; any other string is converted when the rule runs, and a name that
    matches nothing fails the rule;
  - a `List` or `Map` field takes the list or map object as it is, such as
    [`tagList()`](tal-sw-native.md#functions-of-the-native-input), without copying it.

  A field the block does not set keeps the source's constructor default: `null` for most objects, an empty list for
  `Segment`'s tags.
- **`timeBucket` is required**, `minuteTimeBucket(...)` for a metrics source and `recordTimeBucket(...)` for a record
  such as `DatabaseSlowStatement`, as the agent analysis sets them. A `source {}` without it is a compile error.
- **Nothing is formatted implicitly.** A name is stored as the expression gives it. A rule calls `formatServiceName`,
  `formatInstanceName` and `formatEndpointName` where it needs the core's naming rules.
- **The block holds field statements only.** A value that depends on a condition is computed into a `def` first.
- **Where it is written.** On `otlp`, in the extractor: it is built and sent at its statement, as `metrics {}` is, so
  an `abort {}` or a failure later in the rule leaves it sent. On `sw-native`, inside
  [`build {}`](tal-sw-native.md#build): it is built and sent at its statement in the segment's build phase, so a later
  failure in the same block leaves it sent.

```text
source ServiceMeta {
  name db
  layer "VIRTUAL_DATABASE"
  timeBucket minuteTimeBucket(span.startTime)
}
```

## Where each construct applies

An OTLP rule reads attributes and events, overwrites the span's metadata and votes on the stored span; a native rule
reads tags and logs and makes its outputs in the segment's build phase. So the language keeps what every input has,
and each input adds its own:

| | `otlp` | `sw-native` |
|---|---|---|
| span | the OTLP `Span` | the native `SpanObject`, see [the native page](tal-sw-native.md#the-segment-and-its-spans) |
| key and value data | `attr(key, ...)`, `hasAttr(key, ...)`: the span's attributes | `tag(key)`, `hasTag(key)`: the span's tags by name, the first of a repeated key; `tagMap()`: the same as a map; `span.tagsList`: the list |
| events | `event(name)`, `eventAttr(name, key, ...)`: the span's events | `span.logsList`: the span's logs, each with `time` and `dataList` |
| resource and scope | `resource(key, ...)`, `scopeAttr(key, ...)` | none: the agent reports its identity as the segment's `service` and `serviceInstance` |
| constructs | the `meta` root and the statements that overwrite it (`service`, `instance`, `endpoint`, `layer`), `tag`, `persist {}`, `prepareNetworkAlias`, `buildNetworkAlias` and the `pairing` root | `build {}`, `first {}`, `segmentDef`, `writeNetworkAlias` |
| functions | the [functions of the OTLP input](tal-otlp.md#functions-of-the-otlp-input) | the [functions of the native input](tal-sw-native.md#functions-of-the-native-input) |

Every input has `def`, assignment, `if`, `foreach`, `abort {}`, `log`, typed `Layer` and enum values, `metrics {}`,
`source {}`, `requiredModules`, and the [functions for every input](#functions-for-every-input). A construct or a function that a
rule's input does not have is a compile error that names the inputs it belongs to, such as `tag() is a function of the
inputs [sw-native], not of this rule's inputType otlp`. The rules compile at boot, so the misuse fails the boot with
that message and the rule's name.

## Extension points

Two interfaces open TAL to a module, its own or a third party's on the classpath, and a module takes an output of its
own as a source:

- **`TALSourceTypeProvider`** (`trace-analyzer`) is the bridge between TAL and an input, and the point an input is
  extended at. `alias()` is the name a rule writes in `inputType`, and `inputType()` the Java type of its span, whose
  getters the `span` root reads. `constructs()` lists the constructs the input adds. The provider's own public methods
  whose first parameter is the run are the input's functions, named in the input's own words, such as `attr` and
  `tag`. `helperFunctions()` hands the kernel the helper function implementations its rules call beside them: each a
  `TALFunctionProvider` that must serve the input; the shipped inputs both hand over the [functions for every
  input](#functions-for-every-input). A provider is found through
  `META-INF/services/org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider`.
- **`TALFunctionProvider`** (`server-core`) adds functions. It names its module, which must be loaded for its
  functions to be bound, and states the inputs it serves with `supportsInputType(inputType)`, which every
  provider implements. Its public methods are the functions; a method whose first parameter is a
  `TALFunctionContext` receives the run there. A module's providers are found through
  `META-INF/services/org.apache.skywalking.oap.server.core.source.TALFunctionProvider` and bound once its module has
  started; `ai-evaluation`'s, for instance, gives rules `evaluationActive()` and `evaluationSampleRate()`.
- **A source and its dispatcher.** A rule writes an output with [`source {}`](#source-existing-system-adoption). A
  module that takes an output of its own declares the source class in `server-core`, with a `@ScopeDeclaration` whose
  id `DefaultScopeDefine` holds, and a `SourceDispatcher` of it in an `org.apache.skywalking` package, which the source
  receiver finds by scanning the classpath and calls on the rule's thread. `ai-evaluation`'s dispatcher takes
  `GenAIEvaluationSample`.

The compiler resolves a call by name and the rule's input, then by argument count, a fixed count before a
variable-length tail, then among overloads of that count by how well each argument's compile-time type fits its
parameter: the same type fits best, then a supertype, then any numeric widening, all widenings alike, then a scalar
conversion. It types the result from the chosen method's return type. A call that two overloads fit equally well, such
as `f(1)` against `f(long)` and `f(double)`, is a compile error until an `as` settles it, and so is one no overload
fits. A function name that two classes serve for one input fails the boot, among an input's own functions, its
helpers and the modules' providers, and so does one that takes an entity constructor's name, such as `service`. A
function object is stateless across runs and safe to call from every receiver thread.

## Debugging a rule

A rule's run on live traffic can be captured statement by statement through the
[DSL Debug API](../setup/backend/admin-api/dsl-debugging-tal.md).

## Differences from LAL

- No parser blocks. `text {}`, `json {}` and `yaml {}` give a log body structure; a span is already structured.
- No `traceId`, `segmentId`, `spanId` or `timestamp` statements. The identifiers are facts of the span, readable
  through `span` and the input's own roots.
- `abort {}` is allowed anywhere.
- `def` declares, `=` assigns, and `foreach` loops over a list.
- `metrics {}` declares a metric and emits one value; it does not hand a sample to another engine.
- No `sink {}` and no `outputType`: a rule writes a record as a [`source {}`](#source-existing-system-adoption), sent
  at its statement. A LAL rule writes its output when its `sink {}` block ends.
- Each input adds its own blocks: on OTLP, `tag` injects into the stored span rather than into a record, and
  `persist {}` decides the span's own fate with LAL's sink statements, which vote rather than overwrite one another; on
  the native input, `build {}`, `first {}` and `segmentDef`. See the inputs' pages.
- Statements may be separated by `;` as well as by newlines, so a `metrics {}` block fits on one line.
- `!` binds tighter than `&&` and `||`, as in Java: `!peer && hasAttr("x")` reads as `(!peer) && hasAttr("x")`.
- More words are keywords: `persist`, `prepareNetworkAlias`, `buildNetworkAlias`, `scope`, `function`, `match`,
  `status`, `labelLimit`, `buckets`, `ranks`, `source`, `foreach`, `in`, `build`, `first`, `segmentDef`,
  `writeNetworkAlias` and `log`. They still work as field names and label keys, `labels status: ...`, but not as a
  `def` name, so `def status = ...` is a syntax error. `span`, `meta`, `CLIENT` and `SERVER` are keywords too, and work
  as none of these.
