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

package org.apache.skywalking.apm.collector.instrument.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ReportFormatRunner {

    private final Logger logger = LoggerFactory.getLogger(ReportFormatRunner.class);

    public static void main(String[] args) {
        ReportFormatRunner runner = new ReportFormatRunner();
        Report report = runner.readString();

        ReportFormatter formatter = new ReportFormatter();
        formatter.format(report);
    }

    private Report readString() {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        logger.info("Please input the report: ");

        ReportBufferReader reader = new ReportBufferReader();
        try {
            Report report = reader.read(bufferedReader);
            bufferedReader.close();
            return report;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return null;
    }
}
