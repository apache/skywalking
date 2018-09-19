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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * @author liu-xinyuan
 **/
@DefaultImplementor
public class ResetConfListener implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(ResetConfListener.class);
    private File configFile = null;

    @Override public void prepare() throws Throwable {

    }

    @Override public void boot() throws IOException {
        if (Reseter.INSTANCE.getResetPath() != null) {
            Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ResetConfListener"))
                .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                    @Override
                    public void handle(Throwable t) {
                        logger.error("unexpected exception.", t);
                    }
                }), 0, Config.Collector.SERVICE_AND_ENDPOINT_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);

        } else {
            logger.warn("Since the agent.register_status variable is not set correctly, the reset service is not started.");
        }
    }

    @Override public void onComplete() throws Throwable {

    }

    @Override public void shutdown() throws Throwable {

    }

    @Override public void run() {
        logger.debug("ResetConfListener running.");

        try {
            if (Reseter.INSTANCE.predicateReset())
                Reseter.INSTANCE.setStatus(ResetStatus.DONE).clearID().reportToRegisterFile();
        } catch (AgentPackageNotFoundException e) {
            logger.warn(e, "not found package.");
        } catch (SecurityException e) {
            logger.warn(e, "Denise read access to the file {}", configFile);
        } catch (FileNotFoundException e) {
            logger.warn(e, "not found file {}", configFile);
        } catch (IOException e) {
            logger.warn(e.getMessage());
        }

    }

}
