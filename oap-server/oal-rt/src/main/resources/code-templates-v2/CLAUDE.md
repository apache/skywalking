# OAL code templates — always fully qualify

These `.ftl` files render **generated Java source**, compiled by Javassist, not javac.

**Javassist's compiler has no `import` statement.** It resolves a simple class name only against
`ClassPool.importedPackages`, which holds `java.lang` and nothing else. So every class named here
must be fully qualified — `java.util.Map`, `org.apache.skywalking...GateHolder`, all of it.

Shortening one to look tidier still renders, still compiles the surrounding Java, and then fails at
runtime when the rule is compiled. The project's "no inline fully-qualified class names" rule is
about hand-written Java and does not apply to this directory.

Same rule for FQCNs inside codegen string literals in the MAL / LAL / Hierarchy generators.
