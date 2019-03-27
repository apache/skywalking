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
import org.apache.skywalking.oal.tool.parser.*;

public class FileGenerator {
    private List<AnalysisResult> results;
    private DisableCollection collection;
    private String outputPath;
    private Configuration configuration;
    private AllDispatcherContext allDispatcherContext;

    public FileGenerator(OALScripts oalScripts, String outputPath) {
        this.results = oalScripts.getIndicatorStmts();
        this.collection = oalScripts.getDisableCollection();
        this.outputPath = outputPath;
        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(FileGenerator.class.getClassLoader(), "/code-templates");
        allDispatcherContext = new AllDispatcherContext();
        buildDispatcherContext();
    }

    public void generate() throws IOException, TemplateException {
        for (AnalysisResult result : results) {
            generate(result, "Indicator.java", writer -> generateIndicatorImplementor(result, writer));

            String scopeName = result.getSourceName();
            File file = new File(outputPath, "generated/" + scopeName.toLowerCase() + "/" + scopeName + "Dispatcher.java");
            createFile(file);
            generateDispatcher(result, new FileWriter(file));
        }
        generateDisable();
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

    void generateDispatcher(AnalysisResult result, Writer output) throws IOException, TemplateException {
        String scopeName = result.getSourceName();
        DispatcherContext context = allDispatcherContext.getAllContext().get(scopeName);
        if (context != null) {
            configuration.getTemplate("DispatcherTemplate.ftl").process(context, output);
        }
    }

    private void buildDispatcherContext() {
        for (AnalysisResult result : results) {
            String sourceName = result.getSourceName();

            DispatcherContext context = allDispatcherContext.getAllContext().get(sourceName);
            if (context == null) {
                context = new DispatcherContext();
                context.setSource(sourceName);
                context.setPackageName(sourceName.toLowerCase());
                allDispatcherContext.getAllContext().put(sourceName, context);
            }
            context.getIndicators().add(result);
        }
    }

    private void generateDisable() throws IOException, TemplateException {
        File file = new File(outputPath, "generated/DisableSourceDefinition.java");
        createFile(file);
        configuration.getTemplate("DisableSourceDefinition.ftl").process(collection, new FileWriter(file));
    }
}
