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

import freemarker.template.TemplateException;
import java.io.*;
import java.util.*;
import org.apache.skywalking.oal.tool.meta.*;
import org.apache.skywalking.oal.tool.parser.*;
import org.junit.*;

public class FileGeneratorTest {
    @BeforeClass
    public static void init() {
        MetaReader reader = new MetaReader();
        InputStream stream = MetaReaderTest.class.getResourceAsStream("/scope-meta.yml");
        MetaSettings metaSettings = reader.read(stream);
        SourceColumnsFactory.setSettings(metaSettings);
    }

    private AnalysisResult buildResult() {
        AnalysisResult result = new AnalysisResult();
        result.setVarName("generate_indicator");
        result.setSourceName("Service");
        result.setSourceScopeId(1);
        result.setPackageName("service.serviceavg");
        result.setTableName("service_avg");
        result.setSourceAttribute("latency");
        result.setMetricName("ServiceAvg");
        result.setAggregationFunctionName("avg");
        result.setIndicatorClassName("LongAvgIndicator");

        FilterExpression equalExpression = new FilterExpression();
        equalExpression.setExpressionObject("EqualMatch");
        equalExpression.setLeft("source.getName()");
        equalExpression.setRight("\"/service/prod/save\"");
        result.addFilterExpressions(equalExpression);

        FilterExpression greaterExpression = new FilterExpression();
        greaterExpression.setExpressionObject("GreaterMatch");
        greaterExpression.setLeft("source.getLatency()");
        greaterExpression.setRight("1000");
        result.addFilterExpressions(greaterExpression);

        EntryMethod method = new EntryMethod();
        method.setMethodName("combine");
        method.setArgsExpressions(new LinkedList<>());
        method.getArgsExpressions().add("source.getLatency()");
        method.getArgsExpressions().add("1");
        result.setEntryMethod(method);
        result.addPersistentField("summation", "summation", long.class);
        result.addPersistentField("count", "count", int.class);
        result.addPersistentField("value", "value", long.class);
        result.addPersistentField("timeBucket", "time_bucket", long.class);
        result.addPersistentField("stringField", "string_field", String.class);
        result.setFieldsFromSource(SourceColumnsFactory.getColumns("Service"));
        result.generateSerializeFields();

        return result;
    }

    @Test
    public void testGenerateIndicatorImplementor() throws IOException, TemplateException {
        AnalysisResult result = buildResult();

        OALScripts oalScripts = new OALScripts();
        oalScripts.getIndicatorStmts().add(result);

        FileGenerator fileGenerator = new FileGenerator(oalScripts, ".");
        StringWriter writer = new StringWriter();
        fileGenerator.generateIndicatorImplementor(result, writer);
        Assert.assertEquals(readExpectedFile("IndicatorImplementorExpected.java"), writer.toString());

        // fileGenerator.generateIndicatorImplementor(result, new OutputStreamWriter(System.out));
    }

    @Test
    public void testServiceDispatcher() throws IOException, TemplateException {
        AnalysisResult result = buildResult();

        OALScripts oalScripts = new OALScripts();
        oalScripts.getIndicatorStmts().add(result);

        FileGenerator fileGenerator = new FileGenerator(oalScripts, ".");
        StringWriter writer = new StringWriter();
        fileGenerator.generateDispatcher(result, writer);
        Assert.assertEquals(readExpectedFile("ServiceDispatcherExpected.java"), writer.toString());

//        fileGenerator.generateDispatcher(result, new OutputStreamWriter(System.out));
    }

    private String readExpectedFile(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(FileGenerator.class.getResourceAsStream("/expectedFiles/" + filename)));

        StringBuilder fileContent = new StringBuilder();
        String sCurrentLine;

        while ((sCurrentLine = reader.readLine()) != null) {
            fileContent.append(sCurrentLine).append("\n");
        }

        return fileContent.toString();
    }
}
