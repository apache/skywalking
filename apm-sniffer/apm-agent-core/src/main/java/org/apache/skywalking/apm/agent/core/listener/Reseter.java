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
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * @author liu-xinyuan
 **/
public enum Reseter {
    INSTANCE;
    private static final ILog logger = LogManager.getLogger(Reseter.class);
    public static final String APPLICATION_ID_NAM = "application_id";
    public static final String INSTANCE_ID_NAME = "instance_id";
    public static final String STATUS_NAME = "status";
    public static final String STATUS_FILE_NAME = "/reset.status";
    public static final String RESET_CHILD_DIR = "/logs/reset.status";
    public static final String COMMENT = "Status has three values: ON (trigger reset), RUNNING (reset in progress), OFF (reset complete or not triggered).\n" +
        "Application_id: application_id of the current agent.\n" +
        "Instance_id: the instanceid of the current agent.";
    public volatile Properties properties = new Properties();
    public String resetPath;
    private ResetStatus status = ResetStatus.OFF;
    private Boolean stopConsume = false;

    public Reseter setStatus(ResetStatus status) {
        this.status = status;
        return this;
    }

    public String getResetPath() throws IOException, AgentPackageNotFoundException {
        File statusDir = new File(Config.Agent.REGISTER_STATUS_DIR);
        if (resetPath == null) {
            if (statusDir.exists() && statusDir.isDirectory()) {
                Config.Agent.REGISTER_STATUS_DIR = new File(statusDir, STATUS_FILE_NAME).getAbsolutePath();
            } else {
                Config.Agent.REGISTER_STATUS_DIR = AgentPackagePath.getPath().getAbsolutePath() + RESET_CHILD_DIR;
            }
            File file = new File(Config.Agent.REGISTER_STATUS_DIR);
            resetPath = file.getAbsolutePath();
            if (file.exists() && file.isFile()) {
                file.delete();
            }
            init();
        }
        return resetPath;
    }

    public void reportToRegisterFile() throws AgentPackageNotFoundException, IOException {
        File configFile = new File(resetPath);
        properties.setProperty(APPLICATION_ID_NAM, RemoteDownstreamConfig.Agent.APPLICATION_ID + "");
        properties.setProperty(INSTANCE_ID_NAME, RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID + "");
        properties.setProperty(STATUS_NAME, status.value());
        FileOutputStream outputStream = new FileOutputStream(configFile);
        properties.store(outputStream, COMMENT);
        outputStream.close();
    }

    public void clearID() throws IOException, AgentPackageNotFoundException {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = DictionaryUtil.nullValue();
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = DictionaryUtil.nullValue();
        status = ResetStatus.RUNNING;
        stopConsume();
        logger.info("clear id successfully,begin trigger reset!");
        reportToRegisterFile();
    }

    Boolean predicateReset() throws AgentPackageNotFoundException, IOException {
        File resetFile = new File(getResetPath());
        if (System.currentTimeMillis() - resetFile.lastModified() < 5 * 1000) {
            FileInputStream inputStream = new FileInputStream(resetFile);
            FileChannel fileChannel = inputStream.getChannel();
            FileLock fileLock = fileChannel.tryLock(0, resetFile.length(), true);
            if (fileLock == null) {
                return false;
            }
            properties.load(inputStream);
            inputStream.close();
            if (properties.get(STATUS_NAME) != null && properties.getProperty(STATUS_NAME).equals(ResetStatus.ON.value())) {
                return true;
            }
        }
        return false;
    }

    public void init() throws IOException {
        properties.setProperty(APPLICATION_ID_NAM, RemoteDownstreamConfig.Agent.APPLICATION_ID + "");
        properties.setProperty(INSTANCE_ID_NAME, RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID + "");
        properties.setProperty(STATUS_NAME, status.value());
        FileOutputStream outputStream = new FileOutputStream(new File(resetPath));
        properties.store(outputStream, COMMENT);
    }

    public void stopConsume() {
        this.stopConsume = true;
    }

    public Boolean getStopConsume() {
        return stopConsume;
    }

    public void enableConsume() {
        this.stopConsume = false;
    }
}
