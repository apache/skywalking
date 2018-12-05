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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.EndpointNameDictionary;
import org.apache.skywalking.apm.agent.core.dictionary.NetworkAddressDictionary;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.TraceSegmentServiceClient;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * @author liu-xinyuan
 **/
public enum Reseter {
    INSTANCE;
    private static final ILog logger = LogManager.getLogger(Reseter.class);
    private static final String SERVICE_ID_NAME = "service_id";
    private static final String INSTANCE_ID_NAME = "instance_id";
    private static final String STATUS_NAME = "status";
    private static final String RESET_CHILD_DIR = "/reset.status";
    private static final String COMMENT = "#Status has three values: ON (trigger reset), DONE(reset complete), OFF(agent fist boot).\n" +
        "service_id: the service_id of the current agent.\n" +
        "Instance_id: the instance_id of the current agent.";
    private volatile Properties properties = new Properties();
    private String resetPath;
    private ResetStatus status = ResetStatus.OFF;
    private boolean isFirstRun = true;
    private int detectDuration = 5000;

    public Reseter setStatus(ResetStatus status) {
        this.status = status;
        return this;
    }

    public String getResetPath() throws IOException, SecurityException {
        if (isFirstRun) {
            if (StringUtil.isEmpty(Config.Agent.REGISTER_STATUS_DIR)) {
                try {
                    Config.Agent.REGISTER_STATUS_DIR = AgentPackagePath.getPath() + "/option";
                } catch (AgentPackageNotFoundException e) {
                    e.printStackTrace();
                }
            }
            File statusDir = new File(Config.Agent.REGISTER_STATUS_DIR);

            if (!statusDir.exists() || !statusDir.isDirectory()) {
                statusDir.mkdir();
            }
            resetPath = statusDir.getAbsolutePath() + RESET_CHILD_DIR;
            init();
            isFirstRun = false;
        }
        return resetPath;
    }

    public void reportToRegisterFile() throws IOException {
        FileOutputStream outputStream = null;
        try {
            File configFile = new File(resetPath);
            properties.setProperty(SERVICE_ID_NAME, RemoteDownstreamConfig.Agent.SERVICE_ID + "");
            properties.setProperty(INSTANCE_ID_NAME, RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID + "");
            properties.setProperty(STATUS_NAME, status.value());
            outputStream = new FileOutputStream(configFile);
            properties.store(outputStream, COMMENT);
        } finally {
            closeFileStream(outputStream);
        }
    }

    public Reseter resetRegisterStatus() {
        RemoteDownstreamConfig.Agent.SERVICE_ID = DictionaryUtil.nullValue();
        RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID = DictionaryUtil.nullValue();
        EndpointNameDictionary.INSTANCE.clearEndpointNameDictionary();
        NetworkAddressDictionary.INSTANCE.clearNetworkAddressDictionary();
        ServiceManager.INSTANCE.findService(TraceSegmentServiceClient.class).clearCache();
        status = ResetStatus.DONE;
        logger.info("clear id successfully,begin trigger reset.");
        return this;
    }

    public Boolean predicateReset() throws IOException, SecurityException {
        File resetFile = new File(getResetPath());
        FileInputStream inputStream = null;
        FileLock fileLock = null;
        FileChannel fileChannel = null;
        if (System.currentTimeMillis() - resetFile.lastModified() < detectDuration) {
            try {
                logger.info("The file reset.status was detected to have been modified in the last {} seconds.", detectDuration);
                inputStream = new FileInputStream(resetFile);
                fileChannel = inputStream.getChannel();
                fileLock = fileChannel.tryLock(0, resetFile.length(), true);
                if (fileLock == null) {
                    return false;
                }
                properties.clear();
                properties.load(inputStream);
            } finally {
                fileLock.release();
                fileChannel.close();
                closeFileStream(inputStream);
            }
            if (properties.get(STATUS_NAME) != null && properties.getProperty(STATUS_NAME).equals(ResetStatus.ON.value())) {
                return true;
            }
        }
        return false;

    }

    public void init() throws IOException {
        FileOutputStream outputStream = null;
        try {
            properties.setProperty(SERVICE_ID_NAME, RemoteDownstreamConfig.Agent.SERVICE_ID + "");
            properties.setProperty(INSTANCE_ID_NAME, RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID + "");
            properties.setProperty(STATUS_NAME, status.value());
            File file = new File(resetPath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdir();
            }
            outputStream = new FileOutputStream(file);
            properties.store(outputStream, COMMENT);
        } finally {
            closeFileStream(outputStream);
        }
    }

    public void closeFileStream(Closeable stream) throws IOException {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new IOException("file close failed.", e);
            }
        } else {
            throw new IOException("create file outputstream failed");
        }
    }
}
