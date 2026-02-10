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
 *
 */

package org.apache.skywalking.oal.v2.generator;

import freemarker.template.Configuration;
import freemarker.template.Version;
import java.io.StringWriter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;

/**
 * Generates Java source code that exactly matches the bytecode produced by {@link OALClassGeneratorV2}.
 *
 * <p>This generator produces complete, compilable Java source files using the same FreeMarker
 * templates used for bytecode generation. The generated source files are useful for:
 * <ul>
 *   <li>Debugging and inspection of generated code</li>
 *   <li>Documentation of the code generation process</li>
 *   <li>Verification that templates produce correct Java syntax</li>
 * </ul>
 *
 * <p>The generated sources are 100% consistent with the bytecode loaded into JVM because
 * they use the identical FreeMarker templates for method body generation.
 *
 * @see OALClassGeneratorV2
 */
@Slf4j
public class OALSourceGenerator {

    private static final String METRICS_FUNCTION_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.metrics.";
    private static final String METRICS_STREAM_PROCESSOR = "org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor";
    private static final String[] METRICS_CLASS_METHODS = {
        "id", "hashCode", "remoteHashCode", "equals", "serialize", "deserialize", "getMeta", "toHour", "toDay"
    };
    private static final String[] METRICS_BUILDER_CLASS_METHODS = {
        "entity2Storage", "storage2Entity"
    };

    private final OALDefine oalDefine;
    private final Configuration configuration;
    private StorageBuilderFactory storageBuilderFactory;

    public OALSourceGenerator(OALDefine define) {
        this.oalDefine = define;
        this.configuration = new Configuration(new Version("2.3.28"));
        this.configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        this.configuration.setClassLoaderForTemplateLoading(
            OALSourceGenerator.class.getClassLoader(), "/code-templates-v2");
    }

    public void setStorageBuilderFactory(StorageBuilderFactory factory) {
        this.storageBuilderFactory = factory;
    }

    /**
     * Generate complete Java source code for a metrics class.
     *
     * <p>The generated source includes:
     * <ul>
     *   <li>Package declaration</li>
     *   <li>Import statements</li>
     *   <li>Class-level @Stream annotation</li>
     *   <li>Class declaration extending the metrics function class</li>
     *   <li>Fields with @Column, @BanyanDB, @ElasticSearch annotations</li>
     *   <li>Getter/setter methods</li>
     *   <li>All template-generated methods (id, hashCode, equals, serialize, etc.)</li>
     * </ul>
     *
     * @param model the code generation model
     * @return complete Java source code as a string
     * @throws OALCompileException if template processing fails
     */
    public String generateMetricsSource(CodeGenModel model) throws OALCompileException {
        StringBuilder source = new StringBuilder();
        String className = model.getMetricsName() + "Metrics";

        // Package declaration (remove trailing dot if present)
        String metricsPackage = oalDefine.getDynamicMetricsClassPackage();
        if (metricsPackage.endsWith(".")) {
            metricsPackage = metricsPackage.substring(0, metricsPackage.length() - 1);
        }
        source.append("package ").append(metricsPackage).append(";\n\n");

        // Imports - match what Javassist would require
        source.append("import org.apache.skywalking.oap.server.core.analysis.Stream;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.")
            .append(model.getMetricsClassName()).append(";\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;\n");
        source.append("import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.annotation.Column;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;\n");
        source.append("\n");

        // Class-level @Stream annotation - exactly as Javassist adds it
        source.append("@Stream(\n");
        source.append("    name = \"").append(model.getTableName()).append("\",\n");
        source.append("    scopeId = ").append(model.getSourceScopeId()).append(",\n");
        source.append("    builder = ").append(model.getMetricsName()).append("MetricsBuilder.class,\n");
        source.append("    processor = MetricsStreamProcessor.class\n");
        source.append(")\n");

        // Class declaration
        source.append("public class ").append(className)
            .append(" extends ").append(model.getMetricsClassName())
            .append(" implements WithMetadata {\n\n");

        // Fields with annotations - exactly as Javassist adds them
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            // @Column annotation
            source.append("    @Column(name = \"").append(field.getColumnName()).append("\"");
            if (field.getType().equals(String.class)) {
                source.append(", length = ").append(field.getLength());
            }
            source.append(")\n");

            // @BanyanDB.SeriesID and @ElasticSearch.EnableDocValues for ID fields
            if (field.isID()) {
                source.append("    @BanyanDB.SeriesID(index = 0)\n");
                source.append("    @ElasticSearch.EnableDocValues\n");
            }

            // @BanyanDB.ShardingKey for sharding key fields
            if (field.isShardingKey()) {
                source.append("    @BanyanDB.ShardingKey(index = ").append(field.getShardingKeyIdx()).append(")\n");
            }

            // Field declaration
            source.append("    private ").append(field.getType().getName())
                .append(" ").append(field.getFieldName()).append(";\n\n");
        }

        // Default constructor
        source.append("    public ").append(className).append("() {\n");
        source.append("    }\n\n");

        // Getter/setter methods for each field
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            String capFieldName = field.getFieldName().substring(0, 1).toUpperCase()
                + field.getFieldName().substring(1);

            // Getter
            source.append("    public ").append(field.getType().getName())
                .append(" get").append(capFieldName).append("() {\n");
            source.append("        return this.").append(field.getFieldName()).append(";\n");
            source.append("    }\n\n");

            // Setter
            source.append("    public void set").append(capFieldName)
                .append("(").append(field.getType().getName()).append(" ").append(field.getFieldName()).append(") {\n");
            source.append("        this.").append(field.getFieldName()).append(" = ").append(field.getFieldName()).append(";\n");
            source.append("    }\n\n");
        }

        // Template-generated methods - exactly what Javassist compiles
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(model, methodEntity);
                source.append("    ").append(methodEntity.toString().trim()).append("\n\n");
            } catch (Exception e) {
                throw new OALCompileException("Failed to generate method " + method + ": " + e.getMessage(), e);
            }
        }

        source.append("}\n");
        return source.toString();
    }

    /**
     * Generate complete Java source code for a metrics builder class.
     *
     * @param model the code generation model
     * @return complete Java source code as a string
     * @throws OALCompileException if template processing fails
     */
    public String generateMetricsBuilderSource(CodeGenModel model) throws OALCompileException {
        if (storageBuilderFactory == null) {
            storageBuilderFactory = new StorageBuilderFactory.Default();
        }

        StringBuilder source = new StringBuilder();
        String className = model.getMetricsName() + "MetricsBuilder";

        // Package declaration (remove trailing dot if present)
        String builderPackage = oalDefine.getDynamicMetricsBuilderClassPackage();
        if (builderPackage.endsWith(".")) {
            builderPackage = builderPackage.substring(0, builderPackage.length() - 1);
        }
        source.append("package ").append(builderPackage).append(";\n\n");

        // Imports
        source.append("import org.apache.skywalking.oap.server.core.storage.StorageData;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;\n");
        String metricsClassFqn = oalDefine.getDynamicMetricsClassPackage() + model.getMetricsName() + "Metrics";
        source.append("import ").append(metricsClassFqn).append(";\n");
        source.append("\n");

        // Class declaration
        source.append("public class ").append(className)
            .append(" implements StorageBuilder {\n\n");

        // Default constructor
        source.append("    public ").append(className).append("() {\n");
        source.append("    }\n\n");

        // Template-generated methods
        for (String method : METRICS_BUILDER_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate(
                    storageBuilderFactory.builderTemplate().getTemplatePath() + "/" + method + ".ftl")
                    .process(model, methodEntity);
                source.append("    ").append(methodEntity.toString().trim()).append("\n\n");
            } catch (Exception e) {
                throw new OALCompileException("Failed to generate method " + method + ": " + e.getMessage(), e);
            }
        }

        source.append("}\n");
        return source.toString();
    }

    /**
     * Generate complete Java source code for a dispatcher class.
     *
     * @param dispatcherContext the dispatcher context with all metrics
     * @return complete Java source code as a string
     * @throws OALCompileException if template processing fails
     */
    public String generateDispatcherSource(OALClassGeneratorV2.DispatcherContextV2 dispatcherContext)
        throws OALCompileException {

        StringBuilder source = new StringBuilder();
        String className = dispatcherContext.getSourceName() + "Dispatcher";

        // Package declaration (remove trailing dot if present)
        String dispatcherPackage = oalDefine.getDynamicDispatcherClassPackage();
        if (dispatcherPackage.endsWith(".")) {
            dispatcherPackage = dispatcherPackage.substring(0, dispatcherPackage.length() - 1);
        }
        source.append("package ").append(dispatcherPackage).append(";\n\n");

        // Imports
        source.append("import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;\n");
        source.append("import org.apache.skywalking.oap.server.core.source.ISource;\n");
        String sourceClassFqn = oalDefine.getSourcePackage() + dispatcherContext.getSourceName();
        source.append("import ").append(sourceClassFqn).append(";\n");

        // Import all generated metrics classes
        for (CodeGenModel metric : dispatcherContext.getMetrics()) {
            String metricsClassFqn = oalDefine.getDynamicMetricsClassPackage() + metric.getMetricsName() + "Metrics";
            source.append("import ").append(metricsClassFqn).append(";\n");
        }
        source.append("\n");

        // Class declaration with generic type
        source.append("public class ").append(className)
            .append(" implements SourceDispatcher<").append(dispatcherContext.getSourceName()).append("> {\n\n");

        // doMetrics methods for each metric
        for (CodeGenModel metric : dispatcherContext.getMetrics()) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("dispatcher/doMetrics.ftl").process(metric, methodEntity);
                source.append("    ").append(methodEntity.toString().trim()).append("\n\n");
            } catch (Exception e) {
                throw new OALCompileException(
                    "Failed to generate doMetrics for " + metric.getMetricsName() + ": " + e.getMessage(), e);
            }
        }

        // dispatch method
        StringWriter dispatchMethod = new StringWriter();
        try {
            configuration.getTemplate("dispatcher/dispatch.ftl").process(dispatcherContext, dispatchMethod);
            source.append("    ").append(dispatchMethod.toString().trim()).append("\n\n");
        } catch (Exception e) {
            throw new OALCompileException("Failed to generate dispatch method: " + e.getMessage(), e);
        }

        source.append("}\n");
        return source.toString();
    }
}
