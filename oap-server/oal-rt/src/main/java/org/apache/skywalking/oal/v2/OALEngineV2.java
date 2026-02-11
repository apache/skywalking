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

package org.apache.skywalking.oal.v2;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.generator.CodeGenModel;
import org.apache.skywalking.oal.v2.generator.MetricDefinitionEnricher;
import org.apache.skywalking.oal.v2.generator.OALClassGeneratorV2;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

/**
 * V2 OAL Engine - completely independent implementation.
 *
 * This engine:
 * 1. Parses OAL scripts using V2 parser (immutable models)
 * 2. Enriches V2 models with metadata (MetricDefinitionEnricher)
 * 3. Generates classes using V2 templates (OALClassGeneratorV2)
 *
 * Benefits:
 * - Clean immutable data models
 * - Type-safe filter values and function arguments
 * - Better error messages with source location tracking
 * - Completely independent (no V1 code dependencies)
 */
@Slf4j
public class OALEngineV2 implements OALEngine {

    private final OALClassGeneratorV2 classGeneratorV2;
    private final OALDefine oalDefine;

    private StreamAnnotationListener streamAnnotationListener;
    private DispatcherDetectorListener dispatcherDetectorListener;
    private final List<Class> metricsClasses;
    private final List<Class> dispatcherClasses;

    public OALEngineV2(OALDefine define) {
        this.oalDefine = define;
        this.classGeneratorV2 = new OALClassGeneratorV2(define);
        this.metricsClasses = new ArrayList<>();
        this.dispatcherClasses = new ArrayList<>();
    }

    @Override
    public void setStreamListener(StreamAnnotationListener listener) {
        this.streamAnnotationListener = listener;
    }

    @Override
    public void setDispatcherListener(DispatcherDetectorListener listener) {
        this.dispatcherDetectorListener = listener;
    }

    @Override
    public void setStorageBuilderFactory(StorageBuilderFactory factory) {
        classGeneratorV2.setStorageBuilderFactory(factory);
    }

    @Override
    public void start(ClassLoader currentClassLoader) throws ModuleStartException, OALCompileException {
        log.info("Starting OAL Engine V2...");

        // Prepare temp folder for generated classes
        classGeneratorV2.prepareRTTempFolder();
        classGeneratorV2.setCurrentClassLoader(currentClassLoader);

        // Load OAL script, parse, and generate classes with proper resource management
        try (Reader reader = ResourceUtils.read(oalDefine.getConfigFile())) {
            // Parse using V2 parser
            OALScriptParserV2 v2Parser;
            try {
                v2Parser = OALScriptParserV2.parse(reader, oalDefine.getConfigFile());
                log.info("V2 Parser: Successfully parsed {} metrics", v2Parser.getMetricsCount());
            } catch (IOException e) {
                throw new ModuleStartException("OAL V2 script parse failure", e);
            }

            // Enrich V2 models with metadata for code generation
            List<CodeGenModel> codeGenModels = enrichMetrics(v2Parser.getMetrics());
            log.info("V2 Enricher: Enriched {} metrics with metadata", codeGenModels.size());

            // Generate classes using V2 generator
            classGeneratorV2.generateClassAtRuntime(
                codeGenModels,
                v2Parser.getDisabledSources(),
                metricsClasses,
                dispatcherClasses
            );

            log.info("OAL Engine V2 started successfully. Generated {} metrics classes, {} dispatcher classes",
                metricsClasses.size(),
                dispatcherClasses.size()
            );
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate " + oalDefine.getConfigFile(), e);
        } catch (IOException e) {
            throw new ModuleStartException("OAL V2 script I/O failure", e);
        }
    }

    @Override
    public void notifyAllListeners() throws ModuleStartException {
        for (Class metricsClass : metricsClasses) {
            try {
                streamAnnotationListener.notify(metricsClass);
            } catch (StorageException e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
        for (Class dispatcherClass : dispatcherClasses) {
            try {
                dispatcherDetectorListener.addIfAsSourceDispatcher(dispatcherClass);
            } catch (Exception e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
    }

    /**
     * Enrich V2 metrics with metadata for code generation.
     */
    private List<CodeGenModel> enrichMetrics(List<MetricDefinition> metrics) {
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
            oalDefine.getSourcePackage(),
            oalDefine.getDynamicMetricsClassPackage()
        );

        List<CodeGenModel> codeGenModels = new ArrayList<>();
        for (MetricDefinition metric : metrics) {
            try {
                CodeGenModel model = enricher.enrich(metric);
                codeGenModels.add(model);

                log.debug("Enriched metric: {} (source: {}, function: {})",
                    metric.getName(),
                    metric.getSource().getName(),
                    metric.getAggregationFunction().getName()
                );
            } catch (Exception e) {
                log.error("Failed to enrich metric: {}", metric.getName(), e);
                throw new IllegalStateException(
                    "Failed to enrich V2 metric: " + metric.getName(), e
                );
            }
        }

        return codeGenModels;
    }

    public OALClassGeneratorV2 getClassGeneratorV2() {
        return classGeneratorV2;
    }
}
