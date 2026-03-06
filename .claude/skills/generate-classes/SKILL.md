---
name: generate-classes
description: Generate bytecode classes from DSL scripts (MAL, OAL, LAL, Hierarchy). Runs the compiler and dumps .class files for inspection.
argument-hint: "<mal|oal|lal|hierarchy|all>"
---

# Generate DSL Classes

Run the v2 compiler (ANTLR4 + Javassist) to generate bytecode classes from DSL scripts and dump `.class` files to disk for inspection.

## Commands by argument

### `mal` — MAL expression classes

```bash
./mvnw test -pl test/script-cases/script-runtime-with-groovy/mal-lal-v1-v2-checker \
  -Dtest=MalComparisonTest -DfailIfNoTests=false -Dcheckstyle.skip
```

Output location: `test/script-cases/scripts/mal/**/*.generated-classes/`

### `oal` — OAL metrics/dispatcher/builder classes

```bash
./mvnw test -pl oap-server/oal-rt \
  -Dtest=RuntimeOALGenerationTest -DfailIfNoTests=false -Dcheckstyle.skip
```

Output location: `oap-server/oal-rt/target/test-classes/metrics/`, `metrics/builder/`, `dispatcher/`

### `lal` — LAL filter/extractor classes

```bash
./mvnw test -pl test/script-cases/script-runtime-with-groovy/mal-lal-v1-v2-checker \
  -Dtest=LalComparisonTest -DfailIfNoTests=false -Dcheckstyle.skip
```

Output location: `test/script-cases/scripts/lal/**/*.generated-classes/`

### `hierarchy` — Hierarchy rule classes

```bash
./mvnw test -pl test/script-cases/script-runtime-with-groovy/hierarchy-v1-v2-checker \
  -Dtest=HierarchyRuleComparisonTest -DfailIfNoTests=false -Dcheckstyle.skip
```

Output location: `test/script-cases/scripts/hierarchy-rule/*.generated-classes/`

### `all` or no argument — generate all DSLs

Run all four commands above sequentially.

## After generation

Print the output location for the requested DSL so the user knows where to find the generated `.class` files. Use `javap` to decompile:

```bash
javap -c -p <path-to-class-file>
```

## Cleaning generated classes

```bash
./mvnw clean -pl test/script-cases/script-runtime-with-groovy/mal-lal-v1-v2-checker,test/script-cases/script-runtime-with-groovy/hierarchy-v1-v2-checker
```
