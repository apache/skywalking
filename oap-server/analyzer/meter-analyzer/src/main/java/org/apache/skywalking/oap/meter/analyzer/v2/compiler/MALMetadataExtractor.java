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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

/**
 * Static AST analysis: extracts compile-time metadata from a MAL expression AST.
 *
 * <p>Walks the AST to collect:
 * <ul>
 *   <li>Sample names referenced (e.g. {@code instance_jvm_cpu})</li>
 *   <li>Scope type and labels from entity calls (e.g. {@code .service(['svc'], Layer.GENERAL)})</li>
 *   <li>Aggregation labels from {@code .sum(['tag'])}, {@code .avg(['tag'])}</li>
 *   <li>Downsampling type from {@code .downsampling(SUM)}</li>
 *   <li>Histogram/percentile info from {@code .histogram()}, {@code .histogram_percentile([50,75,90])}</li>
 * </ul>
 *
 * <p>Also generates the {@code metadata()} method source that returns
 * {@link ExpressionMetadata} at runtime.
 */
public final class MALMetadataExtractor {

    private MALMetadataExtractor() {
    }

    /**
     * Extracts compile-time metadata from the AST by walking all method chains.
     *
     * <p>Example: for {@code metric.sum(['svc']).service(['svc'], Layer.GENERAL)},
     * extracts samples=["metric"], scopeType=SERVICE, scopeLabels=["svc"],
     * aggregationLabels=["svc"].
     */
    public static ExpressionMetadata extractMetadata(final MALExpressionModel.Expr ast) {
        final Set<String> sampleNames = new LinkedHashSet<>();
        collectSampleNames(ast, sampleNames);

        ScopeType scopeType = null;
        final Set<String> scopeLabels = new LinkedHashSet<>();
        final Set<String> aggregationLabels = new LinkedHashSet<>();
        DownsamplingType downsampling = DownsamplingType.AVG;
        boolean isHistogram = false;
        int[] percentiles = null;

        final List<List<MALExpressionModel.MethodCall>> allChains = new ArrayList<>();
        collectMethodChains(ast, allChains);

        for (final List<MALExpressionModel.MethodCall> chain : allChains) {
            for (final MALExpressionModel.MethodCall mc : chain) {
                final String name = mc.getName();
                switch (name) {
                    case "sum":
                    case "avg":
                    case "max":
                    case "min":
                    case "count":
                        addStringListLabels(mc, aggregationLabels);
                        break;
                    case "service":
                        scopeType = ScopeType.SERVICE;
                        addStringListLabels(mc, scopeLabels);
                        break;
                    case "instance":
                        scopeType = ScopeType.SERVICE_INSTANCE;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "endpoint":
                        scopeType = ScopeType.ENDPOINT;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "process":
                        scopeType = ScopeType.PROCESS;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "serviceRelation":
                        scopeType = ScopeType.SERVICE_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "processRelation":
                        scopeType = ScopeType.PROCESS_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "histogram":
                        isHistogram = true;
                        break;
                    case "histogram_percentile":
                        if (!mc.getArguments().isEmpty()
                                && mc.getArguments().get(0)
                                    instanceof MALExpressionModel.NumberListArgument) {
                            final List<Double> vals =
                                ((MALExpressionModel.NumberListArgument)
                                    mc.getArguments().get(0)).getValues();
                            percentiles = new int[vals.size()];
                            for (int i = 0; i < vals.size(); i++) {
                                percentiles[i] = vals.get(i).intValue();
                            }
                        }
                        break;
                    case "downsampling":
                        if (!mc.getArguments().isEmpty()) {
                            final MALExpressionModel.Argument dsArg =
                                mc.getArguments().get(0);
                            if (dsArg instanceof MALExpressionModel.EnumRefArgument) {
                                final String val =
                                    ((MALExpressionModel.EnumRefArgument) dsArg)
                                        .getEnumValue();
                                downsampling = DownsamplingType.valueOf(val);
                            } else if (dsArg instanceof MALExpressionModel.ExprArgument) {
                                final MALExpressionModel.Expr dsExpr =
                                    ((MALExpressionModel.ExprArgument) dsArg).getExpr();
                                if (dsExpr instanceof MALExpressionModel.MetricExpr) {
                                    final String val =
                                        ((MALExpressionModel.MetricExpr) dsExpr)
                                            .getMetricName();
                                    downsampling = DownsamplingType.valueOf(val);
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // Validate decorate() usage
        boolean hasDecorate = false;
        for (final List<MALExpressionModel.MethodCall> chain : allChains) {
            for (final MALExpressionModel.MethodCall mc : chain) {
                if ("decorate".equals(mc.getName())) {
                    hasDecorate = true;
                    break;
                }
            }
            if (hasDecorate) {
                break;
            }
        }
        if (hasDecorate) {
            if (scopeType != null && scopeType != ScopeType.SERVICE) {
                throw new IllegalStateException(
                    "decorate() should be invoked after service()");
            }
            if (isHistogram) {
                throw new IllegalStateException(
                    "decorate() not supported for histogram metrics");
            }
        }

        return new ExpressionMetadata(
            new ArrayList<>(sampleNames),
            scopeType,
            scopeLabels,
            aggregationLabels,
            downsampling,
            isHistogram,
            percentiles
        );
    }

    /**
     * Generates the {@code metadata()} method source.
     *
     * <p>The generated method builds and returns an {@link ExpressionMetadata} instance
     * with the pre-extracted compile-time facts (sample names, scope, labels, etc.).
     */
    static String generateMetadataMethod(final ExpressionMetadata metadata) {
        final StringBuilder sb = new StringBuilder();
        final String mdClass =
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata";
        final String scopeTypeClass =
            "org.apache.skywalking.oap.server.core.analysis.meter.ScopeType";
        final String dsTypeClass =
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType";

        sb.append("public ").append(mdClass).append(" metadata() {\n");

        sb.append("  java.util.List _samples = new java.util.ArrayList();\n");
        for (final String sample : metadata.getSamples()) {
            sb.append("  _samples.add(\"")
              .append(MALCodegenHelper.escapeJava(sample)).append("\");\n");
        }

        sb.append("  java.util.Set _scopeLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getScopeLabels()) {
            sb.append("  _scopeLabels.add(\"")
              .append(MALCodegenHelper.escapeJava(label)).append("\");\n");
        }

        sb.append("  java.util.Set _aggLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getAggregationLabels()) {
            sb.append("  _aggLabels.add(\"")
              .append(MALCodegenHelper.escapeJava(label)).append("\");\n");
        }

        if (metadata.getPercentiles() != null) {
            sb.append("  int[] _pct = new int[]{");
            for (int i = 0; i < metadata.getPercentiles().length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(metadata.getPercentiles()[i]);
            }
            sb.append("};\n");
        } else {
            sb.append("  int[] _pct = null;\n");
        }

        sb.append("  return new ").append(mdClass).append("(\n");
        sb.append("    _samples,\n");
        if (metadata.getScopeType() != null) {
            sb.append("    ").append(scopeTypeClass).append('.')
              .append(metadata.getScopeType().name()).append(",\n");
        } else {
            sb.append("    null,\n");
        }
        sb.append("    _scopeLabels,\n");
        sb.append("    _aggLabels,\n");
        sb.append("    ").append(dsTypeClass).append('.')
          .append(metadata.getDownsampling().name()).append(",\n");
        sb.append("    ").append(metadata.isHistogram()).append(",\n");
        sb.append("    _pct\n");
        sb.append("  );\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ==================== Private helpers ====================

    /**
     * Recursively collects all metric sample names from the AST.
     */
    private static void collectSampleNames(final MALExpressionModel.Expr expr,
                                            final Set<String> names) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            final MALExpressionModel.MetricExpr me =
                (MALExpressionModel.MetricExpr) expr;
            names.add(me.getMetricName());
            collectSampleNamesFromChain(me.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectSampleNames(
                ((MALExpressionModel.BinaryExpr) expr).getLeft(), names);
            collectSampleNames(
                ((MALExpressionModel.BinaryExpr) expr).getRight(), names);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectSampleNames(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), names);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            final MALExpressionModel.ParenChainExpr pce =
                (MALExpressionModel.ParenChainExpr) expr;
            collectSampleNames(pce.getInner(), names);
            collectSampleNamesFromChain(pce.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    private static void collectSampleNamesFromChain(
            final List<MALExpressionModel.MethodCall> chain,
            final Set<String> names) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if ("downsampling".equals(mc.getName())) {
                continue;
            }
            for (final MALExpressionModel.Argument arg : mc.getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    /**
     * Recursively collects all method chains from the AST.
     */
    private static void collectMethodChains(
            final MALExpressionModel.Expr expr,
            final List<List<MALExpressionModel.MethodCall>> chains) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            chains.add(((MALExpressionModel.MetricExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectMethodChains(
                ((MALExpressionModel.BinaryExpr) expr).getLeft(), chains);
            collectMethodChains(
                ((MALExpressionModel.BinaryExpr) expr).getRight(), chains);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectMethodChains(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), chains);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectMethodChains(
                ((MALExpressionModel.ParenChainExpr) expr).getInner(), chains);
            chains.add(
                ((MALExpressionModel.ParenChainExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectMethodChains(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), chains);
                }
            }
            chains.add(
                ((MALExpressionModel.FunctionCallExpr) expr).getMethodChain());
        }
    }

    /** Adds labels from the first StringList argument. */
    private static void addStringListLabels(
            final MALExpressionModel.MethodCall mc, final Set<String> target) {
        if (!mc.getArguments().isEmpty()
                && mc.getArguments().get(0)
                    instanceof MALExpressionModel.StringListArgument) {
            target.addAll(
                ((MALExpressionModel.StringListArgument)
                    mc.getArguments().get(0)).getValues());
        }
    }

    /** Adds labels from ALL StringList arguments. */
    private static void addAllStringListLabels(
            final MALExpressionModel.MethodCall mc, final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringListArgument) {
                target.addAll(
                    ((MALExpressionModel.StringListArgument) arg).getValues());
            }
        }
    }

    /** Adds labels from String (non-list) arguments. */
    private static void addStringArgLabels(
            final MALExpressionModel.MethodCall mc, final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringArgument) {
                target.add(
                    ((MALExpressionModel.StringArgument) arg).getValue());
            }
        }
    }
}
