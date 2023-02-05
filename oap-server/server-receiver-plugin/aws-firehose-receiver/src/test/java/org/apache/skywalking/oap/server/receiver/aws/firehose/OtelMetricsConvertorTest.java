/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.aws.firehose;

import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.metrics.firehose.v1.ExportMetricsServiceRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.junit.Assert;
import org.junit.Test;

public class OtelMetricsConvertorTest {

    @Test
    public void test() throws IOException {
        for (TestData testData : findTestData()) {
            io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest request = convertSource(
                testData.getSourceFile());
            String str = JsonFormat.printer().print(request);
            final Map convertedData = new Gson().fromJson(str, Map.class);
            final Map expect = new Gson().fromJson(
                new String(Files.readAllBytes(testData.getExpectFile().toPath())), Map.class);
            Assert.assertEquals(
                String.format("diff , %s -> %s", testData.getSourceFile(), testData.getExpectFile()),
                expect,
                convertedData
            );
            System.out.printf("test pass %s -> %s %n", testData.getSourceFile(), testData.getExpectFile());
        }
    }

    private io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest convertSource(final File sourceFile) throws IOException {
        String source = new String(Files.readAllBytes(sourceFile.toPath()));
        final ExportMetricsServiceRequest.Builder builder = ExportMetricsServiceRequest.newBuilder();
        JsonFormat.parser().merge(source, builder);
        return OtelMetricsConvertor.convertExportMetricsRequest(
            builder.build());
    }

    private List<TestData> findTestData() {
        List<TestData> res = new ArrayList<>();
        Path resourceDirectory = Paths.get("src", "test", "resources", "convertor-test-data");
        final File[] subFiles = resourceDirectory.toFile().listFiles(File::isDirectory);
        if (subFiles == null) {
            return res;
        }
        for (File subFile : subFiles) {
            File sourceFile = new File(subFile.getAbsolutePath(), "source.json");
            File expectFile = new File(subFile.getAbsolutePath(), "expect.json");
            res.add(new TestData(sourceFile, expectFile));
        }
        return res;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class TestData {
        // OTEL 0.7.0
        private File sourceFile;
        private File expectFile;
    }

}

