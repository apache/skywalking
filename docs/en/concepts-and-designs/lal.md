# Log Analysis Language

Log Analysis Language (LAL) in SkyWalking is essentially a Domain-Specific Language (DSL) to analyze logs. You can use
LAL to parse, extract, and save the logs, as well as collaborate the logs with traces (by extracting the trace ID,
segment ID and span ID) and metrics (by generating metrics from the logs and sending them to the meter system).

The LAL config files are in YAML format, and are located under directory `lal`. You can
set `log-analyzer/default/lalFiles` in the `application.yml` file or set environment variable `SW_LOG_LAL_FILES` to
activate specific LAL config files.

## OTLP log attribute mapping

When logs arrive via the OTLP receiver, resource attributes are mapped to `LogData` fields:

| Resource attribute | LogData field | Notes |
|---|---|---|
| `service.name` | `service` | SkyWalking service name |
| `service.instance.id` | `serviceInstance` | OTel standard ([spec](https://opentelemetry.io/docs/specs/semconv/resource/#service)). Falls back to `service.instance` for backward compatibility. |
| `service.layer` | `layer` | Routes to the LAL rule with matching `layer` declaration |

Log record attributes are available via `tag("attribute_name")` in LAL rules. Attribute keys
retain their original names (dots are NOT converted to underscores in log attributes).

All OTLP resource attributes are also available via `sourceAttribute("attribute_name")` in LAL rules.
Unlike `tag()` which reads from persistent log record tags (`tagsRawData`), `sourceAttribute()` reads
from non-persistent source context — the values are available during LAL processing but are NOT stored.
Use `tag 'key': sourceAttribute("attr")` in the extractor to selectively persist specific attributes.

## Layer
Layer should be declared in the LAL script to represent the analysis scope of the logs.
LAL rules are routed by layer — only rules matching the incoming log's layer are evaluated.
A rule's `layer` must be a registered layer name (case-sensitive) or `auto`. A rule declaring an unregistered or
missing layer is skipped with a warning at boot, and rejected when applied as a runtime rule.

A log is routed as follows:

1. A log whose layer is set, by the agent's `LogData.layer` or the OTLP `service.layer` resource attribute,
   runs the rules declared for that layer. A log whose layer is not registered is dropped.
2. A log without a layer runs the `layer: auto` rules, see [Auto layer](#auto-layer).
3. When no `layer: auto` rule claims it, it runs the `layer: auto` rules declared `fallback: true`.
4. When no fallback rule claims it either, it is dropped.

The shipped `lal/default.yaml` declares:

- `default` (`layer: GENERAL`), saving the logs that arrive with the `GENERAL` layer;
- `default-fallback` (`layer: auto`, `fallback: true`), saving the logs without a layer under `GENERAL`. Edit
  its extractor to file them under another layer, for example by `sourceAttribute(...)` or `tag(...)`;
- `otlp-general` (`layer: OTLP_GENERAL`), saving the logs of the OpenTelemetry services that set
  `service.layer=OTLP_GENERAL`, the layer their spans are analyzed under.

A rule written for the logs without a layer declares `layer: auto`, with `fallback: true` when it is to take
only what the other auto rules leave, and sets the layer in its extractor, e.g. `extractor { layer "GENERAL" }`.
A `layer: GENERAL` rule sees only the logs that arrive with the `GENERAL` layer.

### Inline layer declarations (`layerDefinitions:`)

A LAL file may declare its own custom layers with a top-level `layerDefinitions:` block.
Each entry is funneled through `Layer.register(name, ordinal, normal)` **before**
the rules in the same file compile, so a LAL file is fully self-describing — a new
monitoring target can land as a single LAL file without an enum edit elsewhere in the OAP
source.

```yaml
layerDefinitions:
  - name: IOT_FLEET    # upper-snake-case, must match [A-Z][A-Z0-9_]*
    ordinal: 1000      # unique across all layers; >= 1000 recommended
    normal: true       # true = agent-installed (default), false = conjectured/virtual

rules:
  - name: iot-fleet-access
    layer: IOT_FLEET
    dsl: |
      filter {
        text { regexp $/(?<status>\d+)\s+(?<path>\S+)/$ }
        sink { sampler { rateLimit("iot-fleet") { rpm 1800 } } }
      }
```

Notes:
- **Storage encoding is the ordinal int**, persisted in BanyanDB / Elasticsearch / JDBC.
  Every OAP node that reads or writes a given layer must agree on its `(name, ordinal)`
  mapping — deploy a LAL file with `layerDefinitions:` identically across all nodes.
- **Identical re-registration is a no-op**, so the same `IOT_FLEET` entry can appear in
  multiple LAL files (and additionally in a MAL file, in `layer-extensions.yml`, or via the
  `LayerExtension` SPI). Conflicting registrations cause OAP boot to fail loudly with the
  offending file in the stack trace.
- **Ordinals 0–50** are in active use by the OAP distribution's built-in layers; **51–999**
  are reserved by convention for future built-ins. External layers should start at `>= 1000`
  — enforcement is not strict, but staying above the reserved band avoids upgrade-time
  collisions.
- `layer: auto` works with extension layers too — the extractor body can call
  `layer "IOT_FLEET"` and the runtime resolves it through the registry.

Three other registration paths exist for layers that are **not** specific to a LAL file: an
operator-managed `layer-extensions.yml`, a `LayerExtension` Java SPI for plugin jars, and
the built-in static fields in `Layer.java` for distribution layers. See
[`Layer.java`](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java)
javadoc for the full picture.

### Auto layer

When `layer: auto` is declared, the rule matches logs where `service.layer` is absent (common for OTLP
sources that don't set this attribute). The script is expected to set the layer in the extractor:

```yaml
rules:
  - name: detect-ios
    layer: auto
    dsl: |
      filter {
        if (sourceAttribute("os.name") != "iOS" && sourceAttribute("os.name") != "iPadOS") {
          abort {}
        }
        extractor {
          layer "IOS"
          instance sourceAttribute("service.version")
          tag 'device.model': sourceAttribute("device.model.identifier")
        }
        sink {}
      }
```

In `layer: auto` mode, `abort` and `drop` have distinct meanings:

- **`abort {}`** — "not mine, let others try." If all auto rules abort, the log runs the fallback auto rules.
- **`drop {}`** — "mine, but sampled out." The rule claimed the log (the fallback auto rules do not run) but
  chose not to persist it (e.g., sampling). Use this for rate limiting or conditional dropping
  within a recognized log type.

If an auto rule claims the log (doesn't abort) but doesn't set a layer, the log is warned and
dropped at persistence time.

Every rule a log runs, auto or declared for a layer, starts from the layer the log arrived with, and only its own
`layer` statement changes it: a `layer` statement in one rule never changes the layer another rule saves the log
under, whatever order the rules run in.

In any rule, auto or declared for a layer, a `layer` statement whose value is empty, null (for example
`layer parsed.kind as String` on a log without `kind`) or not a registered layer name drops the log with a
warning when it is saved. A rule without a `layer` statement saves the log under the layer it arrived with.

An auto rule with a `sink` must therefore have a `layer` statement, on some branch of its script. One without
it is skipped with a warning at boot, and refused when applied as a runtime rule. An auto rule without a `sink`,
for example one that only extracts metrics, never saves a log and needs no `layer` statement.

All the auto rules run on such a log, in no defined order, and each one that claims it saves a copy of
its own, so an auto rule should claim only the logs it recognizes and `abort {}` on the others.

A rule declaring `fallback: true` (allowed only with `layer: auto`) runs after the other auto rules, only when
none of them claimed the log. It is the catch-all for the logs without a layer: `default-fallback` in
`lal/default.yaml` files them under `GENERAL`. A fallback rule follows the same rules as any auto rule: it
must set the layer, and `abort {}` leaves the log unclaimed, which drops it. Auto and fallback rule names
share one name space. When no `fallback: true` rule is loaded, the OAP warns at boot, and when a runtime rule
change removes the last one, since the logs without a layer that no auto rule claims are then dropped.

```yaml
rules:
  - name: default-fallback
    layer: auto
    fallback: true
    dsl: |
      filter {
        extractor {
          layer "GENERAL"
        }
        sink {}
      }
```

## Filter

A filter is a group of [parser](#parser), [extractor](#extractor) and [sink](#sink). Each LAL rule holds one filter;
use more rules to organize the processing logic. A piece of log
sent to the filter is available as property `log` in the LAL, therefore you can access the log service name
via `log.service`. For all available fields of `log`, please refer to [the protocol definition](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto#L41).

All components are executed sequentially in the orders they are declared.

### Global Functions

Globally available functions may be used them in all components (i.e. parsers, extractors, and sinks) where necessary.

- `abort`

By default, all components declared are executed no matter what flags (`dropped`, `saved`, etc.) have been set. There
are cases where you may want the filter chain to stop earlier when specified conditions are met. `abort` function aborts
the remaining filter chain from where it's declared, and all the remaining components won't be executed at all.
`abort` function serves as a fast-fail mechanism in LAL.

```
filter {
    if (log.service == "TestingService") { // Don't waste resources on TestingServices
        abort {} // all remaining components won't be executed at all
    }
    // ... parsers, extractors, sinks
}
```

`regexp` is a statement of a [`text`](#text) parser, not a condition: to stop on a log it does not match, rely on
`abortOnFailure`, which is `true` by default.

- `tag`

`tag` function provide a convenient way to get the value of a tag key.

We can add tags like following:
``` JSON
[
   {
      "tags":{
         "data":[
            {
               "key":"TEST_KEY",
               "value":"TEST_VALUE"
            }
         ]
      },
      "body":{
         ...
      }
      ...
   }
]
```
And we can use this method to get the value of the tag key `TEST_KEY`.
```
filter {
    if (tag("TEST_KEY") == "TEST_VALUE") {
         ...
    }
}
```

- `sourceAttribute`

`sourceAttribute` function provides access to non-persistent source context attributes. For OTLP logs,
these are the resource attributes (e.g., `os.name`, `device.model.identifier`). Unlike `tag()`, these
values are NOT persisted in `tagsRawData` — they are only available during LAL processing.

```
filter {
    if (sourceAttribute("os.name") == "iOS") {
        extractor {
            layer "IOS"
            tag 'device.model': sourceAttribute("device.model.identifier")
        }
    }
    sink {}
}
```

Returns empty string if the key is not found.

### Values, casts and conditions

LAL evaluates values as the Groovy-based LAL did, with the differences noted below. That LAL compiled rules
statically (`parsed.*` read at run time), so where an expression's types are known when compiled, they decide.

- A bare condition, `if (parsed.flag)`, uses Groovy truth: `null`, `false`, `0`, `NaN`, an empty string and an empty
  collection are false.
- `as Boolean` converts a boolean, the text `true` or `false` in any case, and a number, or a text holding one
  (trimmed), by zero: `0` is `false`, any other number `true` (`2`, `"-1"`); any other value, such as `"yes"`, `NaN` or a
  list, fails the log. A blank text is `false`, and a `null` stays `null` (false in a condition). A bare condition
  parses nothing: `if (parsed.flag)` tests the value as it is, so a text `"false"` is `true` there.
- A cast keeps a `null`: `parsed.missing as Integer` is `null`, not `0`. In a comparison a `null` equals only a `null` and
  orders below every value, so `parsed.missing as Integer == 0` is false, `parsed.missing as Integer < 1` is true and
  `parsed.n as Integer > null` is true for any number. Arithmetic on a `null`, or passing one where a number is required
  (a metric's value, a primitive method argument), fails the log's processing; concatenated, it reads `"null"`. A tag or
  label whose value is `null` is not added, and a statement that sets a number or enum field (such as an output type's
  `latency`) leaves the field unset. `timestamp` leaves the log's timestamp unset when its value is empty, not a number
  (without a pattern), or not matched by the pattern. A numeric cast of a string trims it first: `" 42 " as Long` is
  `42`, and a blank one fails the log (a missing `tag("k")` is `""`). A primitive cast (`as long`, `as int`,
  `as double`, `as float`, `as boolean`) converts the same way, but a `null` fails it, and is `false` as a `boolean`.
- `==` compares numbers by value whatever their types (`3 == 3L`, `1.9 != 1`), and, as every DSL does, lists and arrays
  element by element in order (a list against an array too), sets by their elements in any order and maps by their
  keys and each key's value, the elements the same way (`[1] == [1.0]`); a JSON value (`toJson(...)`) compares as what
  it holds, an array as a list, an object as a map, a primitive as its number, text or boolean. A string against a number equals the number it holds, trimmed (`"3" == 3` and
  `"200" == 200` are true), and a string that holds no number equals no number; Groovy compared a one-character
  string by its character code (`"A" == 65`) and never equated a longer one. Two strings compare as text
  (`"3" != "3.0"`). `<`, `>`, `<=` and `>=` order numbers by value, and a string against a number as the number it
  holds (`"3" < 5` is true); a string that holds no number fails the compile when it is a literal (`"abc" < 5`), and
  the log otherwise. Two strings order as numbers when both hold one (`"10" > "2"`), and as text otherwise. A
  character (`charAt(0)`) is the one-character string it is in `==` and in an ordering (`"1a".charAt(0) == 1`,
  `"ERROR".charAt(0) == "E"`). A character or a boolean is not cast as a number, which fails the compile (Groovy read a
  character as its code); `c.toString() as Integer` is the number a digit is. A string cannot be multiplied (Groovy
  repeated it, `'b' * 2` is `bb`).
- Numbers compare as Java compares them, as LAL always did: a `NaN` compares false in every comparison but `!=`, held
  in a `def` or written inline alike, and `-0.0` equals `0.0`, so `parsed.x as Double > 1` is false for `NaN` and
  `parsed.x as Double == 0` is true for `-0.0`. A float literal compares by its float value: `0.1f` is not `0.1`.
- `+` concatenates as soon as one side is a string, onto a string, a number, a character or `null` on its left, and
  any value but an array, a collection or a map on its right (`"x" + true`); any other value on the left (a boolean,
  an enum, any other object), and an array, a collection or a map on either side, fails, the compile where its type is
  known, else the log (`true + "x"`, `tag("a").split(",") + "x"`, `"x" + parsed.items`), and so does a boolean beside
  a number. On two values whose types are known only at run time, which the
  Groovy-based LAL did not compile, numbers add (`1 + parsed.n` is `6` for a JSON `5`) and a string, a number, a
  character or a `null` on the left concatenates. A `float` computes in `double`, even with another `float`, as in Groovy. `/` on two whole
  numbers is Groovy's `BigDecimal` (`7 / 2` is `3.5`): exact where the quotient ends, else rounded half up to ten
  decimals, or to an operand's scale when larger (`1 / 3` is `0.3333333333`), and it stays one in `+`, `-`, `*` and `/` with whole numbers (`7 / 1` is `7`); with a
  `float` or `double` it computes in `double`. Cast to `Integer` or `Long`, or set to a whole-number field, it truncates
  toward zero (`((0 - 7) / 2) as Integer` is `-3`) and an `Integer` wraps as Groovy's did; a zero divisor fails the
  log's processing.
- `parsed.<key>` reads the regexp's group of that name (a name the regexp has no group of fails the log's processing, a
  group that did not take part reads on), else the parsed body's value, else a typed input's field (an Envoy access
  log a json parser did not parse), else the log's own field (`service`, `serviceInstance`, `endpoint`, `layer`,
  `timestamp`, `traceContext`, `body`, `tags`) as the rule left it, as `log.<field>` reads it. So a key the body does
  not have, or a parser that did not run (in an `if` branch), reads the log, and a rule may use both a `text` and a
  `json` parser. With only a `text` parser, `parsed.<group>` is a string: a group the match did not set reads only the
  log's string fields (`service`, `serviceInstance`, `endpoint`, `layer`), `null` otherwise. After a `text` and a
  `json` branch rejoin, `toString()` takes the value as it is, and a string method (`toLowerCase()`) takes it as a
  string, failing the log for a json value that is not one. `parsed.getMap()` is the parsed body with the log's fields
  copied in when it was parsed, read only.
- A property after `.` fails on a `null`, as in Groovy: `parsed.a.b` fails the log's processing when `a` is missing,
  while `parsed?.a?.b` is `null`. A property of a map reads its key, so `m.size` is the `size` key of a map `m` and
  `m.size()` its number of entries; a property of a list is the list's own (`parsed.items.empty`, and on a list
  known when compiled `xs.size` or `xs.isEmpty`), or else each element's (`parsed.backends.name`); any other value's
  property is its getter's (`parsed.body.json.json`), among those the next section lists.
- A subscript of a list past its end, `xs[5]`, is `null`; of a string, an array or a `toJsonArray(...)` past its end, or
  of a `null`, it fails.
  An array's `length` reads its length.
- A method is resolved by the type the value has when compiled, among those the next section lists: on a value of
  unknown type, such as a JSON field, only `toString()`, and the null-safe `?.toString()` and `?.trim()`. Give the value
  a type first, such as `parsed.x as String`, or `def id = parsed.id as java.math.BigInteger` to call `id?.toString(16)`.
  What a typed method returns as an `Object` (`m.get("k")` of a map) takes `?.trim()` only cast `as String`.
- A class cast in a `def` converts as Groovy's `as` does: a list cast `as java.util.Set` becomes a set, a numeric
  string cast `as java.math.BigInteger` a number, a name cast to an enum its constant; a conversion Groovy could not
  make fails the log's processing, as does a string cast to a list or a set (Groovy split it into characters). The
  classes are those the next section lists.
- A whole number literal must fit a `long`; Groovy read a larger one as a `BigInteger`. One with a leading `0` is
  octal, as in Groovy and Java (`010` is `8`), anywhere a whole number is written (an index, an argument, `rpm`), and
  `08` or `09` is refused; a decimal such as `010.5` is not octal.
- A `def` is visible only after its declaration, within the `if` branch, extractor, or filter level that declares it;
  a filter-level `def` is also visible in the `extractor` and `sink` blocks that follow it. A `def` of a name that is
  visible there fails the compile, as in Groovy LAL; sibling `if` branches may each declare one.
- A standard field statement (`service`, `instance`, `endpoint`, `traceId`, `segmentId`, `spanId`, `timestamp`, as
  `layer` already did) sets the field of the log itself, for the rest of the rule: a later `log.service`,
  `log.getService()`, `log.traceContext` (the metadata's trace context), `log.getTraceContext()` (the log's protobuf
  one), `parsed.service` (when the body has no `service`) or `log.timestamp`
  (such as a metric's) reads the rule's value, and the service, instance and endpoint traffic of a saved log is built
  from it. Its value is converted by the rule's cast, then to the field's type, so `spanId parsed.span as Integer` is
  valid. A `null` value leaves the field as it was, except for `layer`: a `null`, empty or unregistered layer drops a
  saved log.
- A `tag` statement evaluates all its values, then adds its tags: `tag("key")` and `log.tags` read a tag added by an
  earlier statement, not one of the same statement.
- The statements of a `metrics` block run in the order written; a repeated statement keeps its last value.
- A decimal literal such as `1.5` or `1e6` is a `double`, and arithmetic with it computes in `double`. The
  Groovy-based LAL made it a `BigDecimal`; the two differ only in rare precision cases, such as `0.1 + 0.2`, which is
  `0.30000000000000004` here and `0.3` there, or a literal written `1.50`, which reads `1.5` here.
- Difference from the Groovy-based LAL: a protobuf message (a field of a typed input such as an Envoy access log) is true
  in a bare condition only when it has a field set. Groovy treated every message as true, and a protobuf getter never
  returns `null`, so a guard such as `if (parsed?.response?.responseCode)` could never be false; here it is false when the
  access log carries no response code.
- Differences from the Groovy-based LAL that v2 has always had, kept: a map in a string, by `${...}` or `as String`,
  reads as Java writes it (`{a=1}`, Groovy `[a:1]`), and `toString()` on a `null` fails the log (Groovy
  `"null"`); an array in `${...}` reads as Java writes it (`as String` and `toString()` write its elements, `[low, high]`, as
  Groovy); an iterator
  is true in a condition (Groovy: while it has a next element); `log.traceContext` is the metadata's trace context (`traceId`, `traceSegmentId`, `spanId`, `otlpSpanId`),
  not the protobuf; an unset standard string field reads `null` (Groovy `""`); `parsed.map` is the parsed body's `map`
  key (Groovy: the whole body); and numbers compare as Java's primitives, so `NaN` equals nothing, `NaN` included, and
  `-0.0` equals `0.0` (Groovy ordered a `NaN` after every number).

### Supported operators and functions

A rule calls only what this section lists; anything else fails its compile, naming what is supported. A property read
at run time (a JSON field's `.x`) reads only a map's key, a list's own property or its elements', and the getters
listed here, else it fails the log's processing.

- Operators: `+ - * /`, `== != < > <= >=`, `&& || !`, `.key`, `?.key`, `[N]` and the casts `as String`, `Long`,
  `Integer`, `Double`, `Float`, `Boolean`, and `long`, `int`, `double`, `float`, `boolean`. A `def` may also be cast
  `as java.util.List`, `java.util.Set`, `java.util.Map`, `java.math.BigInteger`, `java.math.BigDecimal`, a protobuf
  message class (to read a typed input's field through its getters), or an enum one of the output type's setters
  takes. There is no `%` and no `Math`: a text is read as a number by a cast (`parsed.n as Long`).
- Functions: `tag("key")`, `sourceAttribute("key")`, `toJson(value)`, `toJsonArray(value)`, and
  `ProcessRegistry.generateVirtualLocalProcess(service, instance)`,
  `ProcessRegistry.generateVirtualRemoteProcess(service, instance, address)` and
  `ProcessRegistry.generateVirtualProcess(service, instance, name)`.
- On a string: `length()`, `isEmpty()`, `trim()`, `toLowerCase()`, `toUpperCase()`, `startsWith()`, `endsWith()`,
  `contains()`, `indexOf()`, `lastIndexOf()`, `substring()`, `replace()`, `replaceAll()`, `replaceFirst()`, `split()`,
  `charAt()`, `equals()`, `equalsIgnoreCase()`, `matches()`, `concat()` and `toString()`. A quoted string is the text
  between its quotes, so `"42".matches("\d+")` passes the regexp `\d+`.
- On a map: `get()`, `containsKey()`, `getOrDefault()`, `isEmpty()`, `size()`, `keySet()`, `values()`; on a list or a
  set: `get()` (a list), `size()`, `isEmpty()`, `contains()`; on an array, `length`. They read only: `parsed.getMap()`
  is the one method of `parsed`, and the map it returns is read only too.
- On any JSON value: `getAsString()`, `getAsLong()`, `getAsInt()`, `getAsDouble()`, `getAsBoolean()`,
  `getAsJsonObject()`, `getAsJsonArray()`, `isJsonNull()`, `isJsonObject()`, `isJsonArray()` (on a `toJsonArray(...)`,
  the `getAs...` ones read its only element); on `toJson(...)` also `get()`, `has()`, `size()`; on `toJsonArray(...)`
  also `get()` and `size()`.
- On `log`, a typed input (a protobuf message, or an object implementing `ToJson`) and the protobuf messages they hold:
  their getters (`getX()`, `hasX()`, `getXCount()`, `getXList()`, `getXMap()`, `getXOrBuilder()`, and the indexed
  or keyed `getX(index)`, `getXOrDefault(key, default)`, `containsX(key)`), read only: the extractor's statements set
  the log, and `log.getX()` reads it as they left it. On the log's metadata (`log.traceContext`, `parsed.*` without a
  parser), its getters.
- On a number, a boolean or a character: `toString()` (a `BigInteger`'s `toString(radix)` too); on an array,
  `toString()` writes its elements (`[low, high]`); on an enum, `name()`, and a protobuf enum's `getNumber()`.

This is the set this release supports, and it grows as rules need more: propose an addition in an issue or a pull
request.

### Parser

Parsers are responsible for parsing the raw logs into structured data in SkyWalking for further processing. There are 3
types of parsers at the moment, namely `json`, `yaml`, and `text`.

When a piece of log is parsed, there is a corresponding property available, called `parsed`, injected by LAL.
Property `parsed` is typically a map, containing all the fields parsed from the raw logs. For example, if the parser
is `json` / `yaml`, `parsed` is a map containing all the key-values in the `json` / `yaml`; if the parser is `text`
, `parsed` is a map containing all the captured groups and their values (for `regexp` and `grok`).

All parsers share the following options:

| Option | Type | Description | Default Value |
| ------ | ---- | ----------- | ------------- |
| `abortOnFailure` | `boolean` | Whether the filter chain should abort if the parser failed to parse / match the logs | `true` |

See examples below.

#### `json`

```
filter {
    json {
        abortOnFailure true // this is optional because it's default behaviour
    }
}
```

`json` reads the JSON body of the [native log protocol](../api/log-data-protocol.md). When the JSON body is empty but a plain-text body is present — for example, the [OTLP log receiver](../setup/backend/log-otlp.md) delivers every string body as text, even JSON-shaped ones — the parser tries the text body as JSON instead. A parse failure of either form follows `abortOnFailure`.

When `json` parses successfully from a text body, the log is normalized to a JSON body for the matching rule: the rule persists it with content type `JSON` and `log.body` reads the JSON content within that rule. This makes a JSON-shaped log delivered over any transport persist and render as JSON once a `json {}` rule matches it. The normalization is scoped to the matching rule — other rules analyzing the same log still see the original text body.

A parse failure that aborts the log is reported at WARN, rate-limited to one report per minute per parser with the number of suppressed failures included in the next report. With `abortOnFailure false`, a failure is expected control flow: it is only logged at DEBUG, the log continues through the filter chain, and `parsed.*` reads return the log's own fields (or `null`).

#### `yaml`

```
filter {
    yaml {
        abortOnFailure true // this is optional because it's default behaviour
    }
}
```

#### `text`

For unstructured logs, there are some `text` parsers for use.

- `regexp`

`regexp` parser uses a regular expression (`regexp`) to parse the logs. It leverages the captured groups of the regexp,
all the captured groups can be used later in the extractors or sinks.
A log the regexp does not match aborts the filter chain, unless the `text` block has `abortOnFailure false`, written
before or after `regexp`. A `text` block takes one `regexp`.

A quoted string is the text between its quotes, its backslashes kept, so `"\d"` is the regexp `\d` and `"\s"` any
whitespace; a slashy string, `$/(?<status>\d+)/$`, keeps every backslash as written too. In a `rateLimit` id, `\${`
keeps `${` as text.

```
filter {
    text {
        abortOnFailure true // this is optional because it's default behaviour
        // this is just a demo pattern
        regexp "(?<timestamp>\\d{8}) (?<thread>\\w+) (?<level>\\w+) (?<traceId>\\w+) (?<msg>.+)"
    }
    extractor {
        tag level: parsed.level
        // we add a tag called `level` and its value is parsed.level, captured from the regexp above
        traceId parsed.traceId
        // we also extract the trace id from the parsed result, which will be used to associate the log with the trace
    }
    // ...
}
```

### Extractor

Extractors aim to extract metadata from the logs. The metadata can be a service name, a service instance name, an
endpoint name, or even a trace ID, all of which can be associated with the existing traces and metrics.

#### Local variables (`def`)

You can use `def` to declare local variables in the extractor (or at the filter level). This is useful when an
expression is reused multiple times, or when you want to break a long chain into readable steps.

The syntax is:

```
def variableName = expression
def variableName = expression as TypeName
```

The variable type is inferred from the initializer expression at compile time. `def` is not limited to JSON — it works
with any value access expression whose type is resolvable on the classpath, including protobuf getter chains,
`log.*` fields, and Gson JSON method chains. Subsequent method calls on the variable are validated at compile time
against the inferred type.

You can optionally add an explicit `as` type cast to narrow the variable type. The cast type can be a built-in
type (`String`, `Long`, `Integer`, `Double`, `Float`, `Boolean`, or a primitive such as `long`) or a fully qualified
class name:

```
def value = someExpression as com.example.MyType
```

This is useful when the compiler infers a type that is too general (e.g., `Object` from a generic API return)
and you know the concrete runtime type. The cast tells the compiler which type to use for subsequent method chain
validation. A built-in type converts the value as in any other cast; a class converts it as Groovy's `as` does (see
[Values, casts and conditions](#values-casts-and-conditions)). For JSON conversion, use `toJson()` or `toJsonArray()`.

The FQCN must be resolvable on the classpath at compile time. If the class is not found, the OAP server will fail
to start.

Two built-in conversion functions are provided for JSON interoperability:

- `toJson(expr)` — converts a value to a Gson `JsonObject`. Works with JSON strings, `Map`, and protobuf `Struct`.
- `toJsonArray(expr)` — converts a value to a Gson `JsonArray`. Works with JSON array strings and `List`.

After declaration, the variable can be used in subsequent expressions with full null-safe navigation support (`?.`).

Def variables can also be used as method arguments. This is useful when you need to look up a dynamic key:

```
filter {
    json {}
    extractor {
        def key = parsed.fieldName as String
        def config = toJson(parsed.metadata)
        tag 'val': config?.get(key)?.getAsString()
    }
    sink {}
}
```

Example — extracting fields from a protobuf input type (no JSON conversion needed):

```
filter {
    extractor {
        def resp = parsed?.response
        tag 'status.code': resp?.responseCode?.value
        tag 'resp.flags': resp?.responseCodeDetails
    }
    sink {}
}
```

Example — extracting JWT claims from envoy access log filter metadata via `toJson()`:

```
filter {
    extractor {
        def jwt = toJson(parsed?.commonProperties?.metadata
            ?.filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
        def payload = jwt?.getAsJsonObject("payload")
        if (payload != null) {
            tag 'email': payload?.get("email")?.getAsString()
            tag 'group': payload?.get("group")?.getAsString()
        }
    }
    sink {}
}
```

Example — parsing a JSON log body field into a structured object:

```
filter {
    json {}
    extractor {
        def config = toJson(parsed.metadata)
        tag 'env': config?.get("env")?.getAsString()
        tag 'region': config?.getAsJsonObject("location")?.get("region")?.getAsString()
    }
    sink {}
}
```

#### Standard fields

- `service`

`service` extracts the service name from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

- `instance`

`instance` extracts the service instance name from the `parsed` result, and set it into the `LogData`, which will be
persisted (if not dropped) and is used to associate with traces / metrics.

- `endpoint`

`endpoint` extracts the endpoint name from the `parsed` result, and set it into the `LogData`, which will be
persisted (if not dropped) and is used to associate with traces / metrics.

- `traceId`

`traceId` extracts the trace ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if not
dropped) and is used to associate with traces / metrics.

- `segmentId`

`segmentId` extracts the segment ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

- `spanId`

`spanId` extracts the span ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if not
dropped) and is used to associate with traces / metrics. It is a SkyWalking span ID: a number, counted within the segment
`segmentId` names.

- `otlpSpanId`

`otlpSpanId` sets the span ID of an OTLP or Zipkin span, 16 lowercase hex characters, which `spanId` cannot hold. It is
persisted with the log, so the log can be queried by the span through `relatedTrace { traceId, otlpSpanId }`. It is an
[output field](#output-fields) of the default output type rather than a keyword, so it works in rules without a custom
`outputType`:

```
filter {
    text {
        regexp $/trace_id=(?<traceId>[0-9a-f]{32}) span_id=(?<spanId>[0-9a-f]{16})/$
    }
    extractor {
        traceId parsed.traceId as String
        otlpSpanId parsed.spanId as String
    }
}
```

The [OTLP log receiver](../setup/backend/log-otlp.md) sets it from the record's own `span_id`; a rule reads it as
`log.traceContext.otlpSpanId`.

- `timestamp`

`timestamp` extracts the timestamp from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

The parameter of `timestamp` can be a millisecond:
```
filter {
    // ... parser

    extractor {
        timestamp parsed.time as String
    }
}
```
or a datetime string with a specified pattern:
```
filter {
    // ... parser

    extractor {
        timestamp parsed.time as String, "yyyy-MM-dd HH:mm:ss"
    }
}
```

- `layer`

`layer` extracts the [layer](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java) from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with service.

- `tag`

`tag` extracts the tags from the `parsed` result, and set them into the `LogData`. The form of this extractor should look something like this: `tag key1: value, key2: value2`, or `tag 'a.key': value` for a key that is not a name. You may use the properties of `parsed` as values; a key is written as is. A `null` or blank value adds no tag, a list adds its non-null elements joined by commas, a key repeated within one `tag` statement keeps its last value, and `tag("key")` later in the rule reads a tag the rule added (the first one, when two statements add that key).

```
filter {
    // ... parser

    extractor {
        tag level: parsed.level, status: parsed.statusMsg
        tag anotherKey: "anotherConstantValue"
        layer 'GENERAL'
    }
}
```

#### Output fields

When a rule declares a custom `outputType` (see [Output Type](#output-type)), the extractor can set fields specific to
that output type. Any identifier in the extractor that is not a standard field (listed above) is treated as an
**output field assignment**. The syntax is the same as standard fields:

```
fieldName valueExpression as Type
```

The LAL compiler validates at boot time that a matching setter exists on the output type class (e.g., `setStatement(String)`
for field `statement`). If no setter is found, the OAP server will fail to start, ensuring early error detection.

See [Slow SQL](#slow-sql-database-slow-statement) and [Sampled Trace](#sampled-trace-network-profiling) for concrete examples.

#### `metrics`

`metrics` extracts / generates metrics from the logs, and sends the generated metrics to the meter system. You may
configure [MAL](mal.md) for further analysis of these metrics. The dedicated MAL config files are under
directory `log-mal-rules`, and you can set `log-analyzer/default/malFiles` to enable configured files.

```yaml
# application.yml
# ...
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:my-lal-config} # files are under "lal" directory
    malFiles: ${SW_LOG_MAL_FILES:my-lal-mal-config, folder1/another-lal-mal-config, folder2/*} # files are under "log-mal-rules" directory
```

Examples are as follows:

```
filter {
    // ...
    extractor {
        service parsed.serviceName
        metrics {
            name "log_count"
            timestamp parsed.timestamp
            labels level: parsed.level, service: parsed.service, instance: parsed.instance
            value 1
        }
        metrics {
            name "http_response_time"
            timestamp parsed.timestamp
            labels status_code: parsed.statusCode, service: parsed.service, instance: parsed.instance
            value parsed.duration
        }
    }
    // ...
}
```

The extractor above generates a metrics named `log_count`, with tag key `level` and value `1`. After that, you can
configure MAL rules to calculate the log count grouping by logging level like this:

```yaml
# ... other configurations of MAL

metrics:
  - name: log_count_debug
    exp: log_count.tagEqual('level', 'DEBUG').sum(['service', 'instance']).increase('PT1M')
  - name: log_count_error
    exp: log_count.tagEqual('level', 'ERROR').sum(['service', 'instance']).increase('PT1M')

```

The other metrics generated is `http_response_time`, so you can configure MAL rules to generate more useful metrics
like percentiles.

```yaml
# ... other configurations of MAL

metrics:
  - name: response_time_percentile
    exp: http_response_time.sum(['le', 'service', 'instance']).increase('PT5M').histogram().histogram_percentile([50,75,90,95,99])
```

### Sink

Sinks are the persistent layer of the LAL. An explicit `sink {}` block is **required** for any data to be persisted.
Without a `sink {}` block, no data is saved — this applies to all LAL rules, including those using custom `outputType`.

Within the sink, you can use samplers, droppers, and enforcers to control which logs are persisted.
An empty `sink {}` block means all logs are saved unconditionally.

#### Sampler

Sampler allows you to save the logs in a sampling manner. Currently, the following sampling strategies are supported:

- `rateLimit`: samples `n` logs at a maximum rate of 1 minute. `rateLimit("SamplerID")` requires an ID for the sampler.
Sampler declarations with the same ID share the same sampler instance, thus sharing the same `rpm` and resetting logic.
A double-quoted ID may interpolate values, such as `rateLimit("${log.service}:${parsed.code as Integer}")`, one sampler
per value; each `${...}` holds one value, optionally with an `as` cast. This is the only place LAL interpolates: in any
other string `${...}` is literal text, and a single-quoted ID holding `${...}` is refused.
- The Groovy-based LAL's `possibility(percentage)` sampler is not supported: a rule using it fails to compile.

We welcome contributions on more sampling strategies. If multiple samplers are specified, the last one determines the
final sampling result. See examples in [Enforcer](#enforcer).

Example, `rateLimit`:

```
filter {
    // ... parser

    sink {
        sampler {
            if (parsed.service == "ImportantApp") {
                rateLimit("ImportantAppSampler") {
                    rpm 1800  // samples at most 1800 logs per minute for service "ImportantApp"
                }
            } else {
                rateLimit("OtherSampler") {
                    rpm 180   // samples at most 180 logs per minute for other services than "ImportantApp"
                }
            }
        }
    }
}
```

#### Dropper

Dropper is a special sink, meaning that all logs are dropped without any exception. This is useful when you want to
drop debugging logs.

```
filter {
    // ... parser

    sink {
        if (parsed.level == "DEBUG") {
            dropper {}
        } else {
            sampler {
                // ... configs
            }
        }
    }
}
```

Or if you have multiple rules, some of which are for extracting metrics, only one of them has to persist the log.

```yaml
rules:
  - name: persist # rule A: this is for persistence
    layer: GENERAL
    dsl: |
      filter {
        json {}
        sink {
          sampler {
            rateLimit("persist") {
              rpm 6000
            }
          }
        }
      }
  - name: metrics # rule B: generates metrics
    layer: GENERAL
    dsl: |
      filter {
        json {}
        extractor {
          metrics {
            name "log_count"
            value 1
          }
        }
        sink {
          dropper {} // drop all logs because they have been saved by rule A above.
        }
      }
```

#### Enforcer

Enforcer is another special sink that forcibly samples the log. A typical use case of enforcer is when you have
configured a sampler and want to save some logs forcibly, such as to save error logs even if the sampling mechanism
has been configured.

```
filter {
    // ... parser

    sink {
        sampler {
            // ... sampler configs
        }
        if (parsed.level == "ERROR" || parsed.userId == "TestingUserId") { // sample error logs or testing users' logs (userId == "TestingUserId") even if the sampling strategy is configured
            enforcer {
            }
        }
    }
}
```

## Output Type

By default, each LAL rule produces a `Log` source object that is persisted to storage. However, some use cases require
transforming log data into a different entity type — for example, converting slow SQL logs into `DatabaseSlowStatement`
records or network profiling logs into `SampledTraceRecord`. The `outputType` mechanism makes this configurable per rule,
without requiring any DSL grammar changes.

### Configuration

Set `outputType` at the rule level in the YAML config. You can use the short name registered by
the `LALOutputBuilder` SPI (recommended), or a fully qualified class name:

```yaml
rules:
  - name: my-rule
    layer: MYSQL
    outputType: SlowSQL    # short name registered by DatabaseSlowStatementBuilder
    dsl: |
      filter {
        // ...
      }
```

### Resolution order

The output type is resolved per-rule in the following priority:

1. **Per-rule YAML config** — the `outputType` field shown above (highest priority).
   Short names (no `.`) are resolved via `ServiceLoader<LALOutputBuilder>`; fully qualified class names
   are resolved via `Class.forName()` as a fallback.
2. **`LALSourceTypeProvider` SPI** — a default output type registered by receiver plugins for a specific layer
3. **`Log.class`** — the fallback if not specified anywhere

### Two output paths

LAL supports two kinds of output types:

| Output path | Base type | How it works |
|---|---|---|
| **Log path** | Subclass of `AbstractLog` | The sink populates standard log fields (service, instance, endpoint, tags, body, etc.) from `LogData` and persists via `SourceReceiver` |
| **Builder path** | Implements `LALOutputBuilder` | The sink creates the builder, calls `init(LogData, Optional<Object> extraLog, NamingControl)` to pre-populate standard fields, applies output field values via setters, then calls `complete(SourceReceiver)` to validate and dispatch |

The builder path is used when the output type implements the `LALOutputBuilder` interface. This is how SkyWalking's
built-in slow SQL and sampled trace features work.

### Built-in output types

#### Slow SQL (Database Slow Statement)

SkyWalking converts slow SQL logs into `DatabaseSlowStatement` records for database slow query analysis (MySQL, PostgreSQL, Redis, etc.).

Use `outputType: SlowSQL` in your rule config.
The available output fields are: `id`, `statement`, `latency`. Standard fields (`service`, `layer`, `timestamp`) are handled
by the extractor as usual and pre-populated via `init()` from `LogData`.

We require a log tag `"LOG_KIND" = "SLOW_SQL"` to make OAP distinguish slow SQL logs from other log reports.

**Note**, slow SQL sampling would only flag this SQL in the candidate list. The OAP server would run statistic per service
and only persistent the top 50 every 10(controlled by `topNReportPeriod: ${SW_CORE_TOPN_REPORT_PERIOD:10}`) minutes by default.

See bundled LAL scripts for complete examples:
[mysql-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/mysql-slowsql.yaml),
[pgsql-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/pgsql-slowsql.yaml),
[redis-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/redis-slowsql.yaml).

#### Sampled Trace (Network Profiling)

SkyWalking converts network profiling sampled trace logs into `SampledTraceRecord` for process-level network analysis.

Use `outputType: SampledTrace` in your rule config.
The available output fields are: `latency`, `uri`, `reason`, `processId`, `destProcessId`, `detectPoint`, `componentId`.

We require a log tag `"LOG_KIND" = "NET_PROFILING_SAMPLED_TRACE"` to make OAP distinguish sampled trace logs from other log reports.

See bundled LAL scripts for complete examples:
[envoy-als.yaml](../../../oap-server/server-starter/src/main/resources/lal/envoy-als.yaml),
[k8s-service.yaml](../../../oap-server/server-starter/src/main/resources/lal/k8s-service.yaml),
[mesh-dp.yaml](../../../oap-server/server-starter/src/main/resources/lal/mesh-dp.yaml).

### Extending: custom output types

For developers who need to create custom output types (implementing `LALOutputBuilder`, extending
`AbstractLog`, registering `LALSourceTypeProvider` SPI, or defining custom input types), see the
[LAL Extension Developer Guide](../guides/lal-extension.md).

<!--
Developer-facing content below is kept for reference but the canonical guide is lal-extension.md.
-->

<!--
#### Approach 1: Implement `LALOutputBuilder`

Use this when you need full control over validation and dispatching, or when the output is not a simple `AbstractLog` subclass.

1. Create a class that implements `org.apache.skywalking.oap.server.core.source.LALOutputBuilder`:

```java
public class MyCustomBuilder implements LALOutputBuilder {
    public static final String NAME = "MyCustom";

    @Setter @Getter
    private String myField;
    @Setter @Getter
    private long myMetric;

    public MyCustomBuilder() {} // no-arg constructor required

    @Override
    public String name() { return NAME; }

    @Override
    public void init(LogData logData, Optional<Object> extraLog, NamingControl namingControl) {
        // Pre-populate from LogData (service, timestamp, traceId, etc.)
        // extraLog contains the typed input object (e.g., a protobuf message) if available
    }

    @Override
    public void complete(SourceReceiver sourceReceiver) {
        // Validate, build final Source/Record, and dispatch
    }
}
```

2. Register it as a `ServiceLoader` provider so the short name can be used in YAML config.
   Create `META-INF/services/org.apache.skywalking.oap.server.core.source.LALOutputBuilder` containing:
   ```
   com.example.MyCustomBuilder
   ```

3. Reference it in your LAL YAML config by short name:

```yaml
rules:
  - name: my-custom-rule
    layer: GENERAL
    outputType: MyCustom    # short name from name() method
    dsl: |
      filter {
        json {}
        extractor {
          myField parsed.someField as String
          myMetric parsed.someValue as Long
        }
        sink {}
      }
```

The LAL compiler validates at boot time that `setMyField(String)` and `setMyMetric(Long)` exist on `MyCustomBuilder`.
Fully qualified class names (e.g., `outputType: com.example.MyCustomBuilder`) are also supported as a fallback.

#### Approach 2: Extend `AbstractLog`

Use this for simpler cases where you want to produce a log-like record with additional fields:

1. Create a subclass of `AbstractLog` with the extra fields.
2. Set `outputType` to your subclass. Standard log fields are populated automatically.

#### Custom input types

For rules that receive structured protobuf data (not JSON/YAML log bodies), you can configure the input type
so that `parsed.*` generates optimized direct getter calls:

```yaml
rules:
  - name: my-proto-rule
    layer: MY_LAYER
    inputType: com.example.MyProtoMessage
    dsl: |
      filter {
        extractor {
          service parsed.serviceName as String
          endpoint parsed.requestPath as String
        }
      }
```

Alternatively, register a `LALSourceTypeProvider` SPI implementation to set the default input and output types
for an entire layer, so individual rules don't need to repeat the configuration:

```java
public class MyLayerSourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public Layer layer() { return Layer.MY_LAYER; }

    @Override
    public Class<?> inputType() { return MyProtoMessage.class; }

    @Override
    public Class<? extends Source> outputType() { return MyCustomBuilder.class; }
}
```

Register it in `META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`.
-->
