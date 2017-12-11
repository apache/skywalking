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


package org.apache.skywalking.apm.agent.core.logging.core;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;

import java.io.File;
import java.io.IOException;

/**
 * @author wusheng
 */
public class FileWriterTest {

    @BeforeClass
    public static void beforeTestFile() throws IOException {
        Config.Logging.MAX_FILE_SIZE = 10;
        File directory = new File("");
        Config.Logging.DIR = directory.getCanonicalPath() + Constants.PATH_SEPARATOR + "/log-test/";
    }

    @Test
    public void testWriteFile() throws InterruptedException {
        FileWriter writer = FileWriter.get();
        for (int i = 0; i < 100; i++) {
            writer.write("abcd");
        }

        Thread.sleep(10000L);
    }

    @AfterClass
    public static void clear() {
        Config.Logging.MAX_FILE_SIZE = 300 * 1024 * 1024;
        deleteDir(new File(Config.Logging.DIR));
        Config.Logging.DIR = "";
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}
