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
package org.apache.skywalking.apm.agent.core.listener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * @author liu-xinyuan
 **/
public class ResetUtil {
    private static final ILog logger = LogManager.getLogger(ResetUtil.class);

    public static final String RESET_FILE_NAME = "register.status";
    public static final String APPLICATION_ID_NAM = "application_id";
    public static final String INSTANCE_ID_NAME = "instance_id";
    public static final String STATUS_NAME = "status";
    public static String RESET_FILE_ABSOLUTE_PATH = "";

    public static void reportToRegisterFile(String key,
        Object value) throws AgentPackageNotFoundException, IOException {
        // File   new File(Config.Agent.REGISTER_STATUS_DIR);

        File configFile = new File(ResetUtil.RESET_FILE_ABSOLUTE_PATH);
        Properties properties = new Properties();
        FileInputStream inputStream = new FileInputStream(configFile);
        properties.load(inputStream);
        properties.setProperty(key, value.toString());
        inputStream.close();

        FileOutputStream outputStream = new FileOutputStream(configFile);
        FileChannel fileChannel = outputStream.getChannel();
        FileLock lock = fileChannel.tryLock(0, configFile.length(), false);
        try {
            while (true) {
                if (lock != null)
                    break;

                Thread.sleep(100);
                logger.warn("The file {} is locked by another process, please close the file! ", ResetUtil.RESET_FILE_ABSOLUTE_PATH);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
        properties.store(outputStream, null);
        lock.release();
        fileChannel.close();

    }

    public static void varifyResetConfig(Properties properties) throws Exception {
        String registerDir = properties.getProperty("agent.register_status_dir");
        if (registerDir == null) {
            throw new Exception("agent.config is not configured with register_status_dir! ");
        }
        File file = new File(registerDir);
        if (!file.exists()) {
            throw new Exception(registerDir + " not exists!");
        } else if (!file.isDirectory()) {
            throw new Exception(registerDir + " is not directory!");
        } else {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].getName().equals(RESET_FILE_NAME)) {
                    RESET_FILE_ABSOLUTE_PATH = files[i].getAbsolutePath();
                    break;
                }
            }
        }
    }
}
