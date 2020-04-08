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
package org.apache.skywalking.apm.testcase.vertxweb;

import io.vertx.core.Vertx;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.skywalking.apm.testcase.vertxweb.controller.VertxCoreController;

public class Application {

    private static final Logger logger = LogManager.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("Application started");

        System.setProperty("vertx.disableFileCPResolving", "true");
        Vertx.vertx().deployVerticle(new VertxCoreController(), it -> {
            if (it.failed()) {
                it.cause().printStackTrace();
                System.exit(-1);
            }
        });
    }
}
