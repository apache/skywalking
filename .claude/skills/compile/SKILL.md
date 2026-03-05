---
name: compile
description: Build SkyWalking OAP server, run javadoc checks, and verify checkstyle. Use to validate changes before submitting a PR.
argument-hint: "[all|backend|javadoc|checkstyle|module-name]"
---

# Compile & Verify

Build the project and run static checks matching CI.

## Prerequisites

- JDK 11, 17, or 21 (LTS versions)
- Maven 3.6+ (use `./mvnw` wrapper)

## Maven profiles

- `backend` (default): Builds OAP server modules
- `ui` (default): Builds web application
- `dist` (default): Creates distribution packages
- `all`: Builds everything including submodule initialization

## Commands by argument

### `all` or no argument — full CI build

```bash
./mvnw clean flatten:flatten install javadoc:javadoc -B -q -Pall \
  -Dmaven.test.skip \
  -Dcheckstyle.skip \
  -Dgpg.skip
```

### `backend` — backend only (faster)

```bash
./mvnw clean flatten:flatten package -Pbackend,dist -Dmaven.test.skip
```

### `javadoc` — javadoc check only

Javadoc requires delombok output, so `install` must run first:

```bash
./mvnw clean flatten:flatten install javadoc:javadoc -B -q -Pall \
  -Dmaven.test.skip \
  -Dcheckstyle.skip \
  -Dgpg.skip
```

Running `javadoc:javadoc` alone without `install` will miss errors because `${delombok.output.dir}` won't be populated.

### `checkstyle` — checkstyle only

```bash
./mvnw -B -q clean flatten:flatten checkstyle:check
```

### Module name — single module build

```bash
./mvnw clean flatten:flatten package -pl oap-server/analyzer/<module-name> -Dmaven.test.skip
```

## Reading javadoc output

Maven prefixes all javadoc output with `[ERROR]`, but the actual severity is in the message after the line number. Only lines containing `error:` fail the build; lines with `warning:` do not.

```
[ERROR] Foo.java:42: error: bad use of '>'        ← ACTUAL ERROR (must fix)
[ERROR] Foo.java:50: warning: no @param for <T>   ← WARNING (does not fail build)
```

### Common javadoc errors

| Error | Cause | Fix |
|-------|-------|-----|
| `bad use of '>'` | Bare `>` in javadoc HTML (e.g., `->` in `<pre>` blocks) | Use `{@code ->}` or `-&gt;` |
| heading out of sequence | Heading level skips the expected hierarchy | See heading rules below |
| reference not found | `{@link Foo#bar()}` with wrong signature | Match exact parameter types: `{@link Foo#bar(ArgType)}` |

### Javadoc heading rules (JDK 13+)

Strict heading validation was introduced in JDK 13. JDK 11 does **not** enforce it, but JDK 17/21/25 do. Write headings correctly for forward compatibility:

| Javadoc location | Start heading at |
|---|---|
| Class, interface, enum, package, module | `<h2>` |
| Constructor, method, field | `<h4>` |
| Standalone HTML files (`doc-files/`) | `<h1>` |

The generated javadoc page uses `<h1>` for the class name and `<h3>` for member sections (Methods, Fields, etc.), so class-level subsections must use `<h2>` and method-level subsections must use `<h4>` to maintain proper nesting.

## CI reference

CI uses JDK 11 on Linux. The `dist-tar` job runs:

```bash
./mvnw clean flatten:flatten install javadoc:javadoc -B -q -Pall \
  -Dmaven.test.skip \
  -Dcheckstyle.skip \
  -Dgpg.skip
```

The `code-style` job runs:

```bash
./mvnw -B -q clean flatten:flatten checkstyle:check
```
