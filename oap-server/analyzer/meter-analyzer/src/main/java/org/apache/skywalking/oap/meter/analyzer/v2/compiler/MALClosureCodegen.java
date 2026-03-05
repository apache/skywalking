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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.List;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates closure classes for MAL expressions using Javassist bytecode generation.
 *
 * <p>This class handles all closure-related code generation: collecting closures from
 * the AST, compiling each closure into a separate class implementing the appropriate
 * functional interface, and generating closure statement/expression/condition code.
 */
@Slf4j
final class MALClosureCodegen {

    private final ClassPool classPool;
    private final MALClassGenerator generator;

    MALClosureCodegen(final ClassPool classPool, final MALClassGenerator generator) {
        this.classPool = classPool;
        this.generator = generator;
    }

    static final class ClosureInfo {
        final MALExpressionModel.ClosureArgument closure;
        final String interfaceType;
        final String methodName;
        int fieldIndex;

        ClosureInfo(final MALExpressionModel.ClosureArgument closure,
                    final String interfaceType,
                    final String methodName) {
            this.closure = closure;
            this.interfaceType = interfaceType;
            this.methodName = methodName;
        }
    }

    void collectClosures(final MALExpressionModel.Expr expr,
                         final List<ClosureInfo> closures) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            collectClosuresFromChain(
                ((MALExpressionModel.MetricExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getLeft(), closures);
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getRight(), closures);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectClosures(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), closures);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectClosures(
                ((MALExpressionModel.ParenChainExpr) expr).getInner(), closures);
            collectClosuresFromChain(
                ((MALExpressionModel.ParenChainExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            final MALExpressionModel.FunctionCallExpr fce =
                (MALExpressionModel.FunctionCallExpr) expr;
            collectClosuresFromArgs(fce.getFunctionName(),
                fce.getArguments(), closures);
            collectClosuresFromChain(
                fce.getMethodChain(), closures);
        }
    }

    void collectClosuresFromChain(final List<MALExpressionModel.MethodCall> chain,
                                  final List<ClosureInfo> closures) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            collectClosuresFromArgs(mc.getName(), mc.getArguments(), closures);
        }
    }

    void collectClosuresFromArgs(final String methodName,
                                 final List<MALExpressionModel.Argument> args,
                                 final List<ClosureInfo> closures) {
        for (final MALExpressionModel.Argument arg : args) {
            if (arg instanceof MALExpressionModel.ClosureArgument) {
                final String interfaceType;
                if ("forEach".equals(methodName)) {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$ForEachFunction";
                } else if ("instance".equals(methodName)) {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$PropertiesExtractor";
                } else if ("decorate".equals(methodName)) {
                    interfaceType = MALCodegenHelper.DECORATE_FUNCTION_TYPE;
                } else {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$TagFunction";
                }
                final ClosureInfo info = new ClosureInfo(
                    (MALExpressionModel.ClosureArgument) arg,
                    interfaceType, methodName);
                info.fieldIndex = closures.size();
                closures.add(info);
            } else if (arg instanceof MALExpressionModel.ExprArgument) {
                collectClosures(
                    ((MALExpressionModel.ExprArgument) arg).getExpr(), closures);
            }
        }
    }

    /**
     * Adds a closure method to the main class instead of creating a separate class.
     * Returns the generated method name.
     */
    String addClosureMethod(final CtClass mainClass,
                            final String fieldName,
                            final ClosureInfo info) throws Exception {
        final String className = mainClass.getName();
        final MALExpressionModel.ClosureArgument closure = info.closure;
        final List<String> params = closure.getParams();
        final boolean isForEach = MALCodegenHelper.FOR_EACH_FUNCTION_TYPE.equals(info.interfaceType);
        final boolean isPropertiesExtractor =
            MALCodegenHelper.PROPERTIES_EXTRACTOR_TYPE.equals(info.interfaceType);

        if (isForEach) {
            final String methodName = fieldName + "_accept";
            final String elementParam = params.size() >= 1 ? params.get(0) : "element";
            final String tagsParam = params.size() >= 2 ? params.get(1) : "tags";

            final StringBuilder sb = new StringBuilder();
            sb.append("public void ").append(methodName).append("(String ")
              .append(elementParam).append(", java.util.Map ").append(tagsParam)
              .append(") {\n");
            for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                generateClosureStatement(sb, stmt, tagsParam);
            }
            sb.append("}\n");

            if (log.isDebugEnabled()) {
                log.debug("ForEach closure method:\n{}", sb);
            }
            final javassist.CtMethod m = CtNewMethod.make(sb.toString(), mainClass);
            mainClass.addMethod(m);
            generator.addLocalVariableTable(m, className, new String[][]{
                {elementParam, "Ljava/lang/String;"},
                {tagsParam, "Ljava/util/Map;"}
            });
            return methodName;
        } else if (isPropertiesExtractor) {
            final String methodName = fieldName + "_apply";
            final String paramName = params.isEmpty() ? "it" : params.get(0);

            final StringBuilder sb = new StringBuilder();
            sb.append("public java.util.Map ").append(methodName)
              .append("(java.util.Map ").append(paramName).append(") {\n");

            final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
            if (body.size() == 1
                    && body.get(0) instanceof MALExpressionModel.ClosureExprStatement
                    && ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr()
                        instanceof MALExpressionModel.ClosureMapLiteral) {
                final MALExpressionModel.ClosureMapLiteral mapLit =
                    (MALExpressionModel.ClosureMapLiteral)
                        ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
                sb.append("  java.util.Map _result = new java.util.HashMap();\n");
                for (final MALExpressionModel.MapEntry entry : mapLit.getEntries()) {
                    sb.append("  _result.put(\"")
                      .append(MALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
                    generateClosureExpr(sb, entry.getValue(), paramName);
                    sb.append(");\n");
                }
                sb.append("  return _result;\n");
            } else {
                for (final MALExpressionModel.ClosureStatement stmt : body) {
                    generateClosureStatement(sb, stmt, paramName);
                }
                sb.append("  return ").append(paramName).append(";\n");
            }
            sb.append("}\n");

            final javassist.CtMethod m = CtNewMethod.make(sb.toString(), mainClass);
            mainClass.addMethod(m);
            generator.addLocalVariableTable(m, className, new String[][]{
                {paramName, "Ljava/util/Map;"}
            });
            return methodName;
        } else if (MALCodegenHelper.DECORATE_FUNCTION_TYPE.equals(info.interfaceType)) {
            final String methodName = fieldName + "_accept";
            final String paramName = params.isEmpty() ? "it" : params.get(0);

            final StringBuilder sb = new StringBuilder();
            sb.append("public void ").append(methodName).append("(Object _arg) {\n");
            sb.append("  ").append(MALCodegenHelper.METER_ENTITY_FQCN).append(" ")
              .append(paramName).append(" = (").append(MALCodegenHelper.METER_ENTITY_FQCN)
              .append(") _arg;\n");
            for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                generateClosureStatement(sb, stmt, paramName, true);
            }
            sb.append("}\n");

            if (log.isDebugEnabled()) {
                log.debug("Decorate closure method:\n{}", sb);
            }
            final javassist.CtMethod m = CtNewMethod.make(sb.toString(), mainClass);
            mainClass.addMethod(m);
            generator.addLocalVariableTable(m, className, new String[][]{
                {"_arg", "Ljava/lang/Object;"},
                {paramName, "L" + MALCodegenHelper.METER_ENTITY_FQCN.replace('.', '/') + ";"}
            });
            return methodName;
        } else {
            // TagFunction: Map<String,String> apply(Map<String,String> tags)
            final String methodName = fieldName + "_apply";
            final String paramName = params.isEmpty() ? "it" : params.get(0);

            final StringBuilder sb = new StringBuilder();
            sb.append("public java.util.Map ").append(methodName)
              .append("(java.util.Map ").append(paramName).append(") {\n");
            for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return ").append(paramName).append(";\n");
            sb.append("}\n");

            final javassist.CtMethod m = CtNewMethod.make(sb.toString(), mainClass);
            mainClass.addMethod(m);
            generator.addLocalVariableTable(m, className, new String[][]{
                {paramName, "Ljava/util/Map;"}
            });
            return methodName;
        }
    }

    void generateClosureStatement(final StringBuilder sb,
                                  final MALExpressionModel.ClosureStatement stmt,
                                  final String paramName) {
        generateClosureStatement(sb, stmt, paramName, false);
    }

    void generateClosureStatement(final StringBuilder sb,
                                  final MALExpressionModel.ClosureStatement stmt,
                                  final String paramName,
                                  final boolean beanMode) {
        if (stmt instanceof MALExpressionModel.ClosureAssignment) {
            final MALExpressionModel.ClosureAssignment assign =
                (MALExpressionModel.ClosureAssignment) stmt;
            if (beanMode) {
                // Bean setter: me.attr0 = 'value' → me.setAttr0("value")
                final String keyText = MALCodegenHelper.extractConstantKey(assign.getKeyExpr());
                if (keyText != null) {
                    sb.append("    ").append(assign.getMapVar()).append(".set")
                      .append(Character.toUpperCase(keyText.charAt(0)))
                      .append(keyText.substring(1)).append("(");
                    generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                    sb.append(");\n");
                } else {
                    // Fallback to map put for dynamic keys
                    sb.append("    ").append(assign.getMapVar()).append(".put(");
                    generateClosureExpr(sb, assign.getKeyExpr(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                    sb.append(");\n");
                }
            } else {
                sb.append("    ").append(assign.getMapVar()).append(".put(");
                generateClosureExpr(sb, assign.getKeyExpr(), paramName, beanMode);
                sb.append(", ");
                generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                sb.append(");\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureIfStatement) {
            final MALExpressionModel.ClosureIfStatement ifStmt =
                (MALExpressionModel.ClosureIfStatement) stmt;
            sb.append("    if (");
            generateClosureCondition(sb, ifStmt.getCondition(), paramName, beanMode);
            sb.append(") {\n");
            for (final MALExpressionModel.ClosureStatement s : ifStmt.getThenBranch()) {
                generateClosureStatement(sb, s, paramName, beanMode);
            }
            sb.append("    }\n");
            if (!ifStmt.getElseBranch().isEmpty()) {
                sb.append("    else {\n");
                for (final MALExpressionModel.ClosureStatement s : ifStmt.getElseBranch()) {
                    generateClosureStatement(sb, s, paramName, beanMode);
                }
                sb.append("    }\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureReturnStatement) {
            final MALExpressionModel.ClosureReturnStatement retStmt =
                (MALExpressionModel.ClosureReturnStatement) stmt;
            if (retStmt.getValue() == null) {
                sb.append("    return;\n");
            } else {
                if (beanMode) {
                    sb.append("    return ");
                } else {
                    sb.append("    return (java.util.Map) ");
                }
                generateClosureExpr(sb, retStmt.getValue(), paramName, beanMode);
                sb.append(";\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureVarDecl) {
            final MALExpressionModel.ClosureVarDecl vd =
                (MALExpressionModel.ClosureVarDecl) stmt;
            sb.append("    ").append(vd.getTypeName()).append(" ")
              .append(vd.getVarName()).append(" = ");
            generateClosureExpr(sb, vd.getInitializer(), paramName, beanMode);
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureVarAssign) {
            final MALExpressionModel.ClosureVarAssign va =
                (MALExpressionModel.ClosureVarAssign) stmt;
            sb.append("    ").append(va.getVarName()).append(" = ");
            generateClosureExpr(sb, va.getValue(), paramName, beanMode);
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureExprStatement) {
            sb.append("    ");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprStatement) stmt).getExpr(), paramName,
                beanMode);
            sb.append(";\n");
        }
    }

    void generateClosureExpr(final StringBuilder sb,
                             final MALExpressionModel.ClosureExpr expr,
                             final String paramName) {
        generateClosureExpr(sb, expr, paramName, false);
    }

    void generateClosureExpr(final StringBuilder sb,
                             final MALExpressionModel.ClosureExpr expr,
                             final String paramName,
                             final boolean beanMode) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            sb.append('"')
              .append(MALCodegenHelper.escapeJava(((MALExpressionModel.ClosureStringLiteral) expr).getValue()))
              .append('"');
        } else if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            final double val =
                ((MALExpressionModel.ClosureNumberLiteral) expr).getValue();
            if (val == (int) val) {
                sb.append((int) val);
            } else {
                sb.append(val);
            }
        } else if (expr instanceof MALExpressionModel.ClosureBoolLiteral) {
            sb.append(((MALExpressionModel.ClosureBoolLiteral) expr).isValue());
        } else if (expr instanceof MALExpressionModel.ClosureNullLiteral) {
            sb.append("null");
        } else if (expr instanceof MALExpressionModel.ClosureMapLiteral) {
            final MALExpressionModel.ClosureMapLiteral mapLit =
                (MALExpressionModel.ClosureMapLiteral) expr;
            sb.append("java.util.Map.of(");
            for (int i = 0; i < mapLit.getEntries().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                final MALExpressionModel.MapEntry entry = mapLit.getEntries().get(i);
                sb.append('"').append(MALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
                generateClosureExpr(sb, entry.getValue(), paramName, beanMode);
            }
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            generateClosureMethodChain(sb,
                (MALExpressionModel.ClosureMethodChain) expr, paramName, beanMode);
        } else if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            final MALExpressionModel.ClosureBinaryExpr bin =
                (MALExpressionModel.ClosureBinaryExpr) expr;
            sb.append("(");
            generateClosureExpr(sb, bin.getLeft(), paramName, beanMode);
            switch (bin.getOp()) {
                case ADD:
                    sb.append(" + ");
                    break;
                case SUB:
                    sb.append(" - ");
                    break;
                case MUL:
                    sb.append(" * ");
                    break;
                case DIV:
                    sb.append(" / ");
                    break;
                default:
                    break;
            }
            generateClosureExpr(sb, bin.getRight(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureCompTernaryExpr) {
            final MALExpressionModel.ClosureCompTernaryExpr ct =
                (MALExpressionModel.ClosureCompTernaryExpr) expr;
            sb.append("(");
            generateClosureExpr(sb, ct.getLeft(), paramName, beanMode);
            sb.append(MALCodegenHelper.comparisonOperator(ct.getOp()));
            generateClosureExpr(sb, ct.getRight(), paramName, beanMode);
            sb.append(" ? ");
            generateClosureExpr(sb, ct.getTrueExpr(), paramName, beanMode);
            sb.append(" : ");
            generateClosureExpr(sb, ct.getFalseExpr(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureTernaryExpr) {
            final MALExpressionModel.ClosureTernaryExpr ternary =
                (MALExpressionModel.ClosureTernaryExpr) expr;
            sb.append("(((Object)(");
            generateClosureExpr(sb, ternary.getCondition(), paramName, beanMode);
            sb.append(")) != null ? (");
            generateClosureExpr(sb, ternary.getTrueExpr(), paramName, beanMode);
            sb.append(") : (");
            generateClosureExpr(sb, ternary.getFalseExpr(), paramName, beanMode);
            sb.append("))");
        } else if (expr instanceof MALExpressionModel.ClosureElvisExpr) {
            final MALExpressionModel.ClosureElvisExpr elvis =
                (MALExpressionModel.ClosureElvisExpr) expr;
            sb.append("java.util.Optional.ofNullable(");
            generateClosureExpr(sb, elvis.getPrimary(), paramName, beanMode);
            sb.append(").orElse(");
            generateClosureExpr(sb, elvis.getFallback(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureRegexMatchExpr) {
            final MALExpressionModel.ClosureRegexMatchExpr rm =
                (MALExpressionModel.ClosureRegexMatchExpr) expr;
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".regexMatch(String.valueOf(");
            generateClosureExpr(sb, rm.getTarget(), paramName, beanMode);
            sb.append("), \"").append(MALCodegenHelper.escapeJava(rm.getPattern())).append("\")");
        } else if (expr instanceof MALExpressionModel.ClosureExprChain) {
            final MALExpressionModel.ClosureExprChain chain =
                (MALExpressionModel.ClosureExprChain) expr;
            final StringBuilder local = new StringBuilder();
            // Cast to String when the chain has method calls (e.g., .split(), .toString())
            // so Javassist can resolve the method on the concrete type.
            final boolean needsCast = chain.getSegments().stream()
                .anyMatch(s -> s instanceof MALExpressionModel.ClosureMethodCallSeg);
            if (needsCast) {
                local.append("((String) ");
            } else {
                local.append("(");
            }
            generateClosureExpr(local, chain.getBase(), paramName, beanMode);
            local.append(")");
            for (final MALExpressionModel.ClosureChainSegment seg : chain.getSegments()) {
                if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    if ("size".equals(mc.getName()) && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i),
                                paramName, beanMode);
                        }
                        local.append(')');
                    }
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append("[(int) ");
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                        paramName, beanMode);
                    local.append(']');
                }
            }
            sb.append(local);
        } else if (expr instanceof MALExpressionModel.ClosureExprCondition) {
            // A bare condition expression used as a statement (e.g., tags.remove('x')
            // parsed as closureCondition → conditionExpr).  Unwrap and emit the inner
            // expression directly — this is a side-effect call, not a boolean test.
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprCondition) expr).getExpr(),
                paramName, beanMode);
        }
    }

    void generateClosureMethodChain(
            final StringBuilder sb,
            final MALExpressionModel.ClosureMethodChain chain,
            final String paramName,
            final boolean beanMode) {
        final String target = chain.getTarget();
        final String resolvedTarget = MALCodegenHelper.CLOSURE_CLASS_FQCN.getOrDefault(target, target);
        final boolean isClassRef = MALCodegenHelper.CLOSURE_CLASS_FQCN.containsKey(target);
        final List<MALExpressionModel.ClosureChainSegment> segs = chain.getSegments();

        // Static class method call: ProcessRegistry.generateVirtualLocalProcess(...)
        if (isClassRef) {
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    local.append('.').append(mc.getName()).append('(');
                    for (int i = 0; i < mc.getArguments().size(); i++) {
                        if (i > 0) {
                            local.append(", ");
                        }
                        generateClosureExpr(local, mc.getArguments().get(i), paramName,
                            beanMode);
                    }
                    local.append(')');
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                }
            }
            sb.append(local);
            return;
        }

        if (segs.isEmpty()) {
            sb.append(resolvedTarget);
            return;
        }

        if (beanMode) {
            // Bean mode: me.serviceName → me.getServiceName()
            // me.layer.name() → me.getLayer().name()
            // parts[0] → parts[0] (array index works as-is)
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    final String name =
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName();
                    if (target.equals(paramName) || local.toString().contains(".get")) {
                        // Bean property on the closure parameter → getter
                        local.append(".get")
                          .append(Character.toUpperCase(name.charAt(0)))
                          .append(name.substring(1)).append("()");
                    } else {
                        // Field access on a local variable (e.g., parts.length)
                        local.append('.').append(name);
                    }
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append('[');
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(), paramName,
                        beanMode);
                    local.append(']');
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    // Groovy .size() on arrays → Java .length (for local vars)
                    if (!target.equals(paramName)
                            && "size".equals(mc.getName())
                            && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i),
                                paramName, beanMode);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
            return;
        }

        // Local variable access (not closure param, not a class reference):
        // e.g., matcher[0][1] → matcher[(int)0][(int)1]  (plain Java array access)
        // e.g., parts.length → parts.length  (field access)
        // e.g., parts.size() → parts.length  (Groovy .size() on arrays)
        if (!target.equals(paramName) && !isClassRef) {
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append("[(int) ");
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(), paramName,
                        beanMode);
                    local.append(']');
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    // Groovy .size() on arrays → Java .length
                    if ("size".equals(mc.getName()) && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
            return;
        }

        // Map mode (original): tags.key → tags.get("key")
        if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureFieldAccess) {
            final String key =
                ((MALExpressionModel.ClosureFieldAccess) segs.get(0)).getName();
            sb.append("(String) ").append(resolvedTarget).append(".get(\"")
              .append(MALCodegenHelper.escapeJava(key)).append("\")");
        } else if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureIndexAccess) {
            sb.append("(String) ").append(resolvedTarget).append(".get(");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureIndexAccess) segs.get(0)).getIndex(), paramName,
                beanMode);
            sb.append(")");
        } else {
            // General chain: build in a local buffer to support safe navigation.
            // The first FieldAccess/IndexAccess is a map .get() returning String.
            // After that, method calls may return other types (e.g., split() →
            // String[]), so subsequent IndexAccess uses array syntax [(int) index].
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            boolean pastMapAccess = false;
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    final String name = ((MALExpressionModel.ClosureFieldAccess) seg)
                        .getName();
                    if (!pastMapAccess) {
                        final String prior = local.toString();
                        local.setLength(0);
                        local.append("((String) ").append(prior).append(".get(\"")
                          .append(MALCodegenHelper.escapeJava(name)).append("\"))");
                        pastMapAccess = true;
                    } else {
                        local.append('.').append(name);
                    }
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    if (!pastMapAccess) {
                        final String prior2 = local.toString();
                        local.setLength(0);
                        local.append("((String) ").append(prior2).append(".get(");
                        generateClosureExpr(local,
                            ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                            paramName, beanMode);
                        local.append("))");
                        pastMapAccess = true;
                    } else {
                        local.append("[(int) ");
                        generateClosureExpr(local,
                            ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                            paramName, beanMode);
                        local.append(']');
                    }
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    if (mc.isSafeNav()) {
                        final String prior = local.toString();
                        local.setLength(0);
                        local.append("(").append(prior).append(" == null ? null : ")
                          .append("((String) ").append(prior).append(").")
                          .append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
                        }
                        local.append("))");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
        }
    }

    void generateClosureCondition(final StringBuilder sb,
                                  final MALExpressionModel.ClosureCondition cond,
                                  final String paramName) {
        generateClosureCondition(sb, cond, paramName, false);
    }

    void generateClosureCondition(final StringBuilder sb,
                                  final MALExpressionModel.ClosureCondition cond,
                                  final String paramName,
                                  final boolean beanMode) {
        if (cond instanceof MALExpressionModel.ClosureComparison) {
            final MALExpressionModel.ClosureComparison cc =
                (MALExpressionModel.ClosureComparison) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    sb.append(")");
                    break;
                default:
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(MALCodegenHelper.comparisonOperator(cc.getOp()));
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    break;
            }
        } else if (cond instanceof MALExpressionModel.ClosureLogical) {
            final MALExpressionModel.ClosureLogical lc =
                (MALExpressionModel.ClosureLogical) cond;
            sb.append("(");
            generateClosureCondition(sb, lc.getLeft(), paramName, beanMode);
            sb.append(lc.getOp() == MALExpressionModel.LogicalOp.AND ? " && " : " || ");
            generateClosureCondition(sb, lc.getRight(), paramName, beanMode);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureNot) {
            sb.append("!(");
            generateClosureCondition(sb,
                ((MALExpressionModel.ClosureNot) cond).getInner(), paramName, beanMode);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureExprCondition) {
            final MALExpressionModel.ClosureExpr condExpr =
                ((MALExpressionModel.ClosureExprCondition) cond).getExpr();
            if (MALCodegenHelper.isBooleanExpression(condExpr)) {
                generateClosureExpr(sb, condExpr, paramName, beanMode);
            } else {
                sb.append("(");
                generateClosureExpr(sb, condExpr, paramName, beanMode);
                sb.append(" != null)");
            }
        } else if (cond instanceof MALExpressionModel.ClosureInCondition) {
            final MALExpressionModel.ClosureInCondition ic =
                (MALExpressionModel.ClosureInCondition) cond;
            sb.append("java.util.List.of(");
            for (int i = 0; i < ic.getValues().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(MALCodegenHelper.escapeJava(ic.getValues().get(i))).append('"');
            }
            sb.append(").contains(");
            generateClosureExpr(sb, ic.getExpr(), paramName, beanMode);
            sb.append(")");
        }
    }
}
