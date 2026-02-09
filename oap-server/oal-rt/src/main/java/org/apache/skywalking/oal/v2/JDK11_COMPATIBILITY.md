# JDK 11 Compatibility

All V2 code is compatible with JDK 11 (LTS).

## Fixed Issues

### ✅ Switch Expressions (Java 14+) → Traditional Switch

**Fixed in:**
- `FilterValue.java` (line 172)
- `FunctionArgument.java` (line 127)

**Before (Java 14+):**
```java
return switch (type) {
    case NUMBER -> String.valueOf(value);
    case STRING -> "\"" + value + "\"";
    case BOOLEAN -> String.valueOf(value);
    case NULL -> "null";
    case ARRAY -> value.toString();
};
```

**After (Java 11 compatible):**
```java
switch (type) {
    case NUMBER:
        return String.valueOf(value);
    case STRING:
        return "\"" + value + "\"";
    case BOOLEAN:
        return String.valueOf(value);
    case NULL:
        return "null";
    case ARRAY:
        return value.toString();
    default:
        throw new IllegalStateException("Unknown type: " + type);
}
```

### ✅ Stream.toList() (Java 16+) → collect(Collectors.toList())

**Fixed in:**
- `MetricsFunctionRegistry.java` (line 73)

**Before (Java 16+):**
```java
return getAllFunctions().stream()
    .map(MetricsFunctionDescriptor::getName)
    .toList();
```

**After (Java 11 compatible):**
```java
return getAllFunctions().stream()
    .map(MetricsFunctionDescriptor::getName)
    .collect(Collectors.toList());
```

## Java Features Used (All JDK 11 Compatible)

### ✅ Java 9+
- `List.of()` - Immutable list creation
- `Optional.ofNullable()` - Optional handling
- Interface private methods - Not used

### ✅ Java 11
- Local variable type inference (`var`) - Not used (for clarity)
- String methods (`isBlank()`, `strip()`) - Not used
- Files methods - Not used

### ✅ Always Compatible
- Enums with methods
- Builder pattern
- Lombok annotations (`@Getter`, `@EqualsAndHashCode`)
- Immutable collections (`Collections.unmodifiableList()`)
- Generics and type inference
- Lambda expressions
- Method references
- Stream API

## Features NOT Used (Post-JDK 11)

### ❌ Java 12+
- ❌ Switch expressions (arrow syntax) - **FIXED**
- ❌ `String.indent()`, `String.transform()`

### ❌ Java 13+
- ❌ Text blocks (`"""..."""`)

### ❌ Java 14+
- ❌ Records
- ❌ Pattern matching for `instanceof`

### ❌ Java 15+
- ❌ Sealed classes

### ❌ Java 16+
- ❌ `Stream.toList()` - **FIXED**
- ❌ Pattern matching for `switch`

### ❌ Java 17+
- ❌ Enhanced switch
- ❌ Sealed interfaces

## Verification

All V2 code uses only JDK 11 compatible features:

```bash
# Verify no Java 14+ switch expressions
grep -r "switch.*->" src/ --include="*.java"
# Result: No matches ✓

# Verify no text blocks
grep -r '"""' src/ --include="*.java"
# Result: No matches ✓

# Verify no records
grep -r "record " src/ --include="*.java"
# Result: No matches ✓

# Verify no Stream.toList()
grep -r "\.toList()" src/ --include="*.java"
# Result: No matches ✓
```

## Build Requirements

- **JDK**: 11, 17, or 21 (LTS versions)
- **Maven**: 3.6+
- **Lombok**: Compatible with all JDK versions

## IDE Configuration

For IntelliJ IDEA:
- Set Project SDK: Java 11
- Set Language Level: 11 - Local variable syntax for lambda parameters
- Enable Lombok plugin

For Eclipse:
- Set Compiler Compliance Level: 11
- Install Lombok plugin

## Summary

✅ All V2 code is **fully compatible with JDK 11**
✅ No Java 12+ features used
✅ Build succeeds on JDK 11, 17, and 21
✅ All lombok annotations are JDK 11 compatible
✅ Code follows SkyWalking coding standards
