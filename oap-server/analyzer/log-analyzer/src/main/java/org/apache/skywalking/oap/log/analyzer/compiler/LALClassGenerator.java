/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import org.apache.skywalking.oap.log.analyzer.compiler.rt.LalExpressionPackageHolder;
import org.apache.skywalking.oap.log.analyzer.dsl.LalExpression;

/**
 * Generates {@link LalExpression} implementation classes from
 * {@link LALScriptModel} AST using Javassist bytecode generation.
 *
 * <p>Because Javassist cannot compile anonymous inner classes,
 * Consumer callbacks are pre-compiled as separate classes and
 * stored as fields on the main class.
 */
public final class LALClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.log.analyzer.compiler.rt.";

    private static final String FILTER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.dsl.spec.filter.FilterSpec";
    private static final String BINDING =
        "org.apache.skywalking.oap.log.analyzer.dsl.Binding";
    private static final String BINDING_PARSED =
        "org.apache.skywalking.oap.log.analyzer.dsl.Binding.Parsed";

    private final ClassPool classPool;

    public LALClassGenerator() {
        this(ClassPool.getDefault());
    }

    public LALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
    }

    /**
     * Compiles a LAL DSL script into a LalExpression implementation.
     */
    public LalExpression compile(final String dsl) throws Exception {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        return compileFromModel(model);
    }

    /**
     * Compiles from a pre-parsed model.
     */
    public LalExpression compileFromModel(final LALScriptModel model) throws Exception {
        final String className = PACKAGE_PREFIX + "LalExpr_"
            + CLASS_COUNTER.getAndIncrement();

        // Phase 1: Collect all consumer info in traversal order
        final List<ConsumerInfo> consumers = new ArrayList<>();
        collectConsumers(model.getStatements(), consumers);

        // Phase 2: Compile consumer classes
        final List<Object> consumerInstances = new ArrayList<>();
        for (int i = 0; i < consumers.size(); i++) {
            final String consumerName = className + "_C" + i;
            final Object instance = compileConsumerClass(
                consumerName, consumers.get(i));
            consumerInstances.add(instance);
        }

        // Phase 3: Build main class with consumer fields
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.log.analyzer.dsl.LalExpression"));

        for (int i = 0; i < consumers.size(); i++) {
            ctClass.addField(CtField.make(
                "public java.util.function.Consumer _consumer" + i + ";",
                ctClass));
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));
        addHelperMethods(ctClass);

        // Phase 4: Generate execute method referencing consumer fields
        final int[] counter = {0};
        final String executeBody = generateExecuteMethod(model, counter);
        ctClass.addMethod(CtNewMethod.make(executeBody, ctClass));

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        final LalExpression instance = (LalExpression) clazz
            .getDeclaredConstructor().newInstance();

        // Phase 5: Wire consumer fields
        for (int i = 0; i < consumerInstances.size(); i++) {
            clazz.getField("_consumer" + i).set(instance, consumerInstances.get(i));
        }

        return instance;
    }

    // ==================== Consumer info ====================

    private static final class ConsumerInfo {
        final String body;
        final String castType;
        final List<ConsumerInfo> subConsumers;

        ConsumerInfo(final String body, final String castType) {
            this.body = body;
            this.castType = castType;
            this.subConsumers = new ArrayList<>();
        }
    }

    // ==================== Phase 1: Collect consumers ====================

    private void collectConsumers(
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final List<ConsumerInfo> consumers) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            collectConsumerFromStatement(stmt, consumers);
        }
    }

    private void collectConsumerFromStatement(
            final LALScriptModel.FilterStatement stmt,
            final List<ConsumerInfo> consumers) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                final StringBuilder sb = new StringBuilder();
                sb.append("  _t.regexp(\"")
                  .append(escapeJava(tp.getRegexpPattern()))
                  .append("\");\n");
                consumers.add(new ConsumerInfo(sb.toString(),
                    "org.apache.skywalking.oap.log.analyzer.dsl"
                    + ".spec.parser.TextParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            if (((LALScriptModel.JsonParser) stmt).isAbortOnFailure()) {
                consumers.add(new ConsumerInfo(
                    "  _t.abortOnFailure();\n",
                    "org.apache.skywalking.oap.log.analyzer.dsl"
                    + ".spec.parser.JsonParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            if (((LALScriptModel.YamlParser) stmt).isAbortOnFailure()) {
                consumers.add(new ConsumerInfo(
                    "  _t.abortOnFailure();\n",
                    "org.apache.skywalking.oap.log.analyzer.dsl"
                    + ".spec.parser.YamlParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            final LALScriptModel.ExtractorBlock block =
                (LALScriptModel.ExtractorBlock) stmt;
            final StringBuilder sb = new StringBuilder();
            generateExtractorStatementsFlat(sb, block.getStatements());
            consumers.add(new ConsumerInfo(sb.toString(),
                "org.apache.skywalking.oap.log.analyzer.dsl"
                + ".spec.extractor.ExtractorSpec"));
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (!sink.getStatements().isEmpty()) {
                final StringBuilder sb = new StringBuilder();
                generateSinkStatementsFlat(sb, sink.getStatements());
                consumers.add(new ConsumerInfo(sb.toString(),
                    "org.apache.skywalking.oap.log.analyzer.dsl"
                    + ".spec.sink.SinkSpec"));
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
            collectConsumers(ifBlock.getThenBranch(), consumers);
            if (!ifBlock.getElseBranch().isEmpty()) {
                collectConsumers(ifBlock.getElseBranch(), consumers);
            }
        }
    }

    // ==================== Flat code for consumer bodies ====================

    private void generateExtractorStatementsFlat(
            final StringBuilder sb,
            final List<LALScriptModel.ExtractorStatement> stmts) {
        for (final LALScriptModel.ExtractorStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.FieldAssignment) {
                final LALScriptModel.FieldAssignment field =
                    (LALScriptModel.FieldAssignment) stmt;
                sb.append("  _t.").append(field.getFieldType().name().toLowerCase())
                  .append("(");
                generateCastedValueAccess(sb, field.getValue(),
                    field.getCastType());
                if (field.getFormatPattern() != null) {
                    sb.append(", \"")
                      .append(escapeJava(field.getFormatPattern()))
                      .append("\"");
                }
                sb.append(");\n");
            } else if (stmt instanceof LALScriptModel.TagAssignment) {
                final LALScriptModel.TagAssignment tag =
                    (LALScriptModel.TagAssignment) stmt;
                if (tag.getTags().size() == 1) {
                    final Map.Entry<String, LALScriptModel.TagValue> entry =
                        tag.getTags().entrySet().iterator().next();
                    sb.append("  _t.tag(\"")
                      .append(escapeJava(entry.getKey())).append("\", ");
                    generateCastedValueAccess(sb, entry.getValue().getValue(),
                        entry.getValue().getCastType());
                    sb.append(");\n");
                }
            }
        }
    }

    private void generateSinkStatementsFlat(
            final StringBuilder sb,
            final List<LALScriptModel.SinkStatement> stmts) {
        for (final LALScriptModel.SinkStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.EnforcerStatement) {
                sb.append("  _t.enforcer();\n");
            } else if (stmt instanceof LALScriptModel.DropperStatement) {
                sb.append("  _t.dropper();\n");
            }
        }
    }

    // ==================== Phase 2: Compile consumer classes ====================

    private Object compileConsumerClass(final String className,
                                         final ConsumerInfo info) throws Exception {
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get("java.util.function.Consumer"));
        ctClass.addInterface(classPool.get(
            PACKAGE_PREFIX + "BindingAware"));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        ctClass.addField(CtField.make(
            "private " + BINDING + " binding;", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "public void setBinding(" + BINDING + " b) {"
            + " this.binding = b; }", ctClass));
        ctClass.addMethod(CtNewMethod.make(
            "public " + BINDING + " getBinding() {"
            + " return this.binding; }", ctClass));

        addHelperMethods(ctClass);

        final String method = "public void accept(Object arg) {\n"
            + "  " + info.castType + " _t = (" + info.castType + ") arg;\n"
            + info.body
            + "}\n";
        ctClass.addMethod(CtNewMethod.make(method, ctClass));

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        return clazz.getDeclaredConstructor().newInstance();
    }

    // ==================== Phase 4: Generate execute method ====================

    private String generateExecuteMethod(final LALScriptModel model,
                                          final int[] counter) {
        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(Object arg0, Object arg1) {\n");
        sb.append("  ").append(FILTER_SPEC).append(" filterSpec = (")
          .append(FILTER_SPEC).append(") arg0;\n");
        sb.append("  ").append(BINDING).append(" binding = (")
          .append(BINDING).append(") arg1;\n");

        for (final LALScriptModel.FilterStatement stmt
                : model.getStatements()) {
            generateFilterStatement(sb, stmt, counter);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateFilterStatement(final StringBuilder sb,
                                          final LALScriptModel.FilterStatement stmt,
                                          final int[] counter) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                emitConsumerCall(sb, "filterSpec.text", counter);
            } else {
                sb.append("  filterSpec.text();\n");
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            if (((LALScriptModel.JsonParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.json", counter);
            } else {
                sb.append("  filterSpec.json();\n");
            }
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            if (((LALScriptModel.YamlParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.yaml", counter);
            } else {
                sb.append("  filterSpec.yaml();\n");
            }
        } else if (stmt instanceof LALScriptModel.AbortStatement) {
            sb.append("  filterSpec.abort();\n");
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            emitConsumerCall(sb, "filterSpec.extractor", counter);
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink();\n");
            } else {
                emitConsumerCall(sb, "filterSpec.sink", counter);
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            generateIfBlock(sb, (LALScriptModel.IfBlock) stmt, counter);
        }
    }

    private void emitConsumerCall(final StringBuilder sb,
                                   final String methodPrefix,
                                   final int[] counter) {
        final int idx = counter[0]++;
        sb.append("  ((")
          .append(PACKAGE_PREFIX).append("BindingAware) this._consumer")
          .append(idx).append(").setBinding(binding);\n");
        sb.append("  ").append(methodPrefix)
          .append("(this._consumer").append(idx).append(");\n");
    }

    private void generateIfBlock(final StringBuilder sb,
                                  final LALScriptModel.IfBlock ifBlock,
                                  final int[] counter) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        for (final LALScriptModel.FilterStatement s : ifBlock.getThenBranch()) {
            generateFilterStatement(sb, s, counter);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            for (final LALScriptModel.FilterStatement s
                    : ifBlock.getElseBranch()) {
                generateFilterStatement(sb, s, counter);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Helper methods ====================

    private void addHelperMethods(final CtClass ctClass) throws Exception {
        ctClass.addMethod(CtNewMethod.make(
            "private static Object getAt(Object obj, String key) {"
            + "  if (obj == null) return null;"
            + "  if (obj instanceof " + BINDING_PARSED + ")"
            + "    return ((" + BINDING_PARSED + ") obj).getAt(key);"
            + "  if (obj instanceof java.util.Map)"
            + "    return ((java.util.Map) obj).get(key);"
            + "  return null;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static long toLong(Object obj) {"
            + "  if (obj instanceof Number) return ((Number) obj).longValue();"
            + "  if (obj instanceof String) return Long.parseLong((String) obj);"
            + "  return 0L;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static int toInt(Object obj) {"
            + "  if (obj instanceof Number) return ((Number) obj).intValue();"
            + "  if (obj instanceof String) return Integer.parseInt((String) obj);"
            + "  return 0;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static boolean toBool(Object obj) {"
            + "  if (obj instanceof Boolean) return ((Boolean) obj).booleanValue();"
            + "  if (obj instanceof String)"
            + " return Boolean.parseBoolean((String) obj);"
            + "  return obj != null;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static boolean isTruthy(Object obj) {"
            + "  if (obj == null) return false;"
            + "  if (obj instanceof Boolean)"
            + " return ((Boolean) obj).booleanValue();"
            + "  if (obj instanceof String)"
            + " return !((String) obj).isEmpty();"
            + "  if (obj instanceof Number)"
            + " return ((Number) obj).doubleValue() != 0;"
            + "  return true;"
            + "}", ctClass));
    }

    // ==================== Conditions ====================

    private void generateCondition(final StringBuilder sb,
                                    final LALScriptModel.Condition cond) {
        if (cond instanceof LALScriptModel.ComparisonCondition) {
            final LALScriptModel.ComparisonCondition cc =
                (LALScriptModel.ComparisonCondition) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast());
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight());
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast());
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight());
                    sb.append(")");
                    break;
                case GT:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") > ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LT:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") < ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case GTE:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") >= ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LTE:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") <= ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                default:
                    break;
            }
        } else if (cond instanceof LALScriptModel.LogicalCondition) {
            final LALScriptModel.LogicalCondition lc =
                (LALScriptModel.LogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft());
            sb.append(lc.getOp() == LALScriptModel.LogicalOp.AND
                ? " && " : " || ");
            generateCondition(sb, lc.getRight());
            sb.append(")");
        } else if (cond instanceof LALScriptModel.NotCondition) {
            sb.append("!(");
            generateCondition(sb,
                ((LALScriptModel.NotCondition) cond).getInner());
            sb.append(")");
        } else if (cond instanceof LALScriptModel.ExprCondition) {
            sb.append("isTruthy(");
            generateValueAccessObj(sb,
                ((LALScriptModel.ExprCondition) cond).getExpr(),
                ((LALScriptModel.ExprCondition) cond).getCastType());
            sb.append(")");
        }
    }

    private void generateConditionValue(final StringBuilder sb,
                                         final LALScriptModel.ConditionValue cv) {
        if (cv instanceof LALScriptModel.StringConditionValue) {
            sb.append('"')
              .append(escapeJava(
                  ((LALScriptModel.StringConditionValue) cv).getValue()))
              .append('"');
        } else if (cv instanceof LALScriptModel.NumberConditionValue) {
            final double val =
                ((LALScriptModel.NumberConditionValue) cv).getValue();
            sb.append("Long.valueOf(").append((long) val).append("L)");
        } else if (cv instanceof LALScriptModel.BoolConditionValue) {
            sb.append("Boolean.valueOf(")
              .append(((LALScriptModel.BoolConditionValue) cv).isValue())
              .append(")");
        } else if (cv instanceof LALScriptModel.NullConditionValue) {
            sb.append("null");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            generateValueAccessObj(sb,
                ((LALScriptModel.ValueAccessConditionValue) cv).getValue(),
                null);
        }
    }

    private void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            sb.append((long) ((LALScriptModel.NumberConditionValue) cv)
                .getValue()).append("L");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            sb.append("toLong(");
            generateValueAccessObj(sb,
                ((LALScriptModel.ValueAccessConditionValue) cv).getValue(),
                null);
            sb.append(")");
        } else {
            sb.append("0L");
        }
    }

    // ==================== Value access ====================

    private void generateCastedValueAccess(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final String castType) {
        if ("String".equals(castType)) {
            sb.append("String.valueOf(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append("toLong(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Integer".equals(castType)) {
            sb.append("toInt(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Boolean".equals(castType)) {
            sb.append("toBool(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else {
            generateValueAccess(sb, value);
        }
    }

    private void generateValueAccessObj(final StringBuilder sb,
                                         final LALScriptModel.ValueAccess value,
                                         final String castType) {
        if ("String".equals(castType)) {
            sb.append("String.valueOf(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else {
            generateValueAccess(sb, value);
        }
    }

    private void generateValueAccess(final StringBuilder sb,
                                      final LALScriptModel.ValueAccess value) {
        String current;
        if (value.isParsedRef()) {
            current = "binding.parsed()";
        } else if (value.isLogRef()) {
            current = "binding.log()";
        } else {
            final List<LALScriptModel.ValueAccessSegment> segs = value.getChain();
            if (segs.isEmpty()) {
                sb.append("null");
                return;
            }
            current = "binding.parsed()";
        }

        final List<LALScriptModel.ValueAccessSegment> chain = value.getChain();
        if (chain.isEmpty()) {
            sb.append(current);
            return;
        }

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                final String name =
                    ((LALScriptModel.FieldSegment) seg).getName();
                current = "getAt(" + current + ", \""
                    + escapeJava(name) + "\")";
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms =
                    (LALScriptModel.MethodSegment) seg;
                current = current + "." + ms.getName() + "()";
            }
        }
        sb.append(current);
    }

    // ==================== Utilities ====================

    private static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generates the Java source body of the execute method for
     * debugging/testing.
     */
    public String generateSource(final String dsl) {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        final int[] counter = {0};
        return generateExecuteMethod(model, counter);
    }
}
