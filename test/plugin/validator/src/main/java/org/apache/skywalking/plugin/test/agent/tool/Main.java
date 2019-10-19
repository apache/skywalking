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

package org.apache.skywalking.plugin.test.agent.tool;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.DataAssert;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Data;
import org.apache.skywalking.plugin.test.agent.tool.validator.exception.AssertFailedException;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        System.exit(verify() ? 0 : 1);
    }

    static boolean verify() {
        File casePath = new File(ConfigHelper.testCaseBaseDir());
        try {
            if (!casePath.exists()) {
                logger.error("test case dir is not exists or is not directory");
                return false;
            }

            logger.info("start to assert data of test case[{}]", ConfigHelper.caseName());
            File actualData = new File(casePath, "actualData.yaml");
            File expectedData = new File(casePath, "expectedData.yaml");

            if (actualData.exists() && expectedData.exists()) {
                try {
                    DataAssert.assertEquals(Data.Loader.loadData("expectedData.yaml", expectedData),
                            Data.Loader.loadData("actualData.yaml", actualData));
                    return true;
                } catch (AssertFailedException e) {
                    logger.error("\nassert failed.\n{}\n", e.getCauseMessage());
                }
            } else {
                logger.error("assert failed. because actual data {} and expected data {}",
                        actualData.exists() ? "founded" : "not founded",
                        expectedData.exists() ? "founded" : "not founded"
                );
            }
        } catch (Exception e) {
            logger.error("assert test case {} failed.", ConfigHelper.caseName(), e);
        }
        return false;
    }

}
