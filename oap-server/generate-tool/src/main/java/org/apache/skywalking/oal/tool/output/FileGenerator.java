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

package org.apache.skywalking.oal.tool.output;

import freemarker.template.*;
import java.io.*;
import java.util.*;
import org.apache.skywalking.oal.tool.parser.AnalysisResult;

public class FileGenerator {
    private List<AnalysisResult> results;
    private String outputPath;
    private Configuration configuration;
    private DispatcherContext dispatcherContext;

    public FileGenerator(List<AnalysisResult> results, String outputPath) {
        this.results = results;
        this.outputPath = outputPath;
        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(FileGenerator.class.getClassLoader(), "/code-templates");
        this.toDispatchers();
    }

    public void generate() throws IOException, TemplateException {
        for (AnalysisResult result : results) {
            generate(result, "Indicator.java", writer -> generateIndicatorImplementor(result, writer));
        }

        File file = new File(outputPath, "generated/all/AllDispatcher.java");
        createFile(file);
        this.generateAllDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/service/ServiceDispatcher.java");
        createFile(file);
        this.generateServiceDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/servicerelation/ServiceRelationDispatcher.java");
        createFile(file);
        this.generateServiceRelationDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/endpoint/EndpointDispatcher.java");
        createFile(file);
        this.generateEndpointDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/endpointrelation/EndpointRelationDispatcher.java");
        createFile(file);
        this.generateEndpointRelationDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstance/ServiceInstanceDispatcher.java");
        createFile(file);
        this.generateServiceInstanceDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstancerelation/ServiceInstanceRelationDispatcher.java");
        createFile(file);
        this.generateServiceInstanceRelationDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstancejvmcpu/ServiceInstanceJVMCPUDispatcher.java");
        createFile(file);
        this.generateServiceInstanceJVMCPUDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstancejvmmemory/ServiceInstanceJVMMemoryDispatcher.java");
        createFile(file);
        this.generateServiceInstanceJVMMemoryDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstancejvmmemorypool/ServiceInstanceJVMMemoryPoolDispatcher.java");
        createFile(file);
        this.generateServiceInstanceJVMMemoryPoolDispatcher(new FileWriter(file));

        file = new File(outputPath, "generated/serviceinstancejvmgc/ServiceInstanceJVMGCDispatcher.java");
        createFile(file);
        this.generateServiceInstanceJVMGCDispatcher(new FileWriter(file));
    }

    private void generate(AnalysisResult result, String fileSuffix,
        WriteWrapper writeWrapper) throws IOException, TemplateException {
        File file = new File(outputPath, buildSubFolderName(result, fileSuffix));
        createFile(file);
        FileWriter fileWriter = new FileWriter(file);
        try {
            writeWrapper.execute(fileWriter);
        } finally {
            fileWriter.close();
        }
    }

    private void createFile(File file) throws IOException {
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        }
    }

    private String buildSubFolderName(AnalysisResult result, String suffix) {
        return "generated/"
            + result.getSourceName().toLowerCase() + "/"
            + result.getMetricName() + suffix;
    }

    void generateIndicatorImplementor(AnalysisResult result, Writer output) throws IOException, TemplateException {
        configuration.getTemplate("IndicatorImplementor.ftl").process(result, output);
    }

    void generateAllDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("AllDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceRelationDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceRelationDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateEndpointDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("EndpointDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateEndpointRelationDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("EndpointRelationDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceRelationDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceRelationDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceJVMCPUDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceJVMCPUDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceJVMMemoryDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceJVMMemoryDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceJVMMemoryPoolDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceJVMMemoryPoolDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    void generateServiceInstanceJVMGCDispatcher(Writer output) throws IOException, TemplateException {
        configuration.getTemplate("ServiceInstanceJVMGCDispatcherTemplate.ftl").process(dispatcherContext, output);
    }

    private void toDispatchers() {
        dispatcherContext = new DispatcherContext();
        for (AnalysisResult result : results) {
            String sourceName = result.getSourceName();
            switch (sourceName) {
                case "All":
                    dispatcherContext.getAllIndicators().add(result);
                    break;
                case "Service":
                    dispatcherContext.getServiceIndicators().add(result);
                    break;
                case "ServiceRelation":
                    dispatcherContext.getServiceRelationIndicators().add(result);
                    break;
                case "ServiceInstance":
                    dispatcherContext.getServiceInstanceIndicators().add(result);
                    break;
                case "ServiceInstanceRelation":
                    dispatcherContext.getServiceInstanceRelationIndicators().add(result);
                    break;
                case "Endpoint":
                    dispatcherContext.getEndpointIndicators().add(result);
                    break;
                case "EndpointRelation":
                    dispatcherContext.getEndpointRelationIndicators().add(result);
                    break;
                case "ServiceInstanceJVMCPU":
                    dispatcherContext.getServiceInstanceJVMCPUIndicators().add(result);
                    break;
                case "ServiceInstanceJVMMemory":
                    dispatcherContext.getServiceInstanceJVMMemoryIndicators().add(result);
                    break;
                case "ServiceInstanceJVMMemoryPool":
                    dispatcherContext.getServiceInstanceJVMMemoryPoolIndicators().add(result);
                    break;
                case "ServiceInstanceJVMGC":
                    dispatcherContext.getServiceInstanceJVMGCIndicators().add(result);
                    break;
                default:
                    throw new RuntimeException("Unexpected dispatcher");
            }
        }
    }
}
