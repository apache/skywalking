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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * @author liu-xinyuan
 **/
public class ResetConfListener implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(ResetConfListener.class);
    private Properties properties = new Properties();
    private File configFile = null;

    @Override public void prepare() throws Throwable {

    }

    @Override public void boot() throws Throwable {
        Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ResetConfListener"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                @Override
                public void handle(Throwable t) {
                    logger.error("unexpected exception.", t);
                }
            }), 0, Config.Collector.APP_AND_SERVICE_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);

    }

    @Override public void onComplete() throws Throwable {

    }

    @Override public void shutdown() throws Throwable {

    }

    @Override public void run() {
        logger.debug("ResetConfListener running.");

        try {
            configFile = new File(ResetUtil.RESET_FILE_ABSOLUTE_PATH);
            if (System.currentTimeMillis() - configFile.lastModified() < 5 * 1000) {

                properties.load(new FileInputStream(configFile));
                if (properties.get("status") != null && properties.get("status").toString().equals("register")) {
                    clearID();
                }
                properties.clear();
            }

        } catch (AgentPackageNotFoundException e) {
            logger.error(e, "not found package!");
        } catch (SecurityException e) {
            logger.error(e, "Denise read access to the file {}", configFile);
        } catch (FileNotFoundException e) {
            logger.error(e, "not found file {}", configFile);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

    }

    private void clearID() throws IOException, AgentPackageNotFoundException {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = DictionaryUtil.nullValue();
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = DictionaryUtil.nullValue();
        ResetUtil.reportToRegisterFile(ResetUtil.APPLICATION_ID_NAM, DictionaryUtil.nullValue());
        ResetUtil.reportToRegisterFile(ResetUtil.INSTANCE_ID_NAME, DictionaryUtil.nullValue());
        ResetUtil.reportToRegisterFile(ResetUtil.STATUS_NAME, "");
    }

}
