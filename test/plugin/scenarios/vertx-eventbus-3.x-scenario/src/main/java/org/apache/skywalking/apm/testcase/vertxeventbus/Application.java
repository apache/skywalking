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

package org.apache.skywalking.apm.testcase.vertxeventbus;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import org.apache.skywalking.apm.testcase.vertxeventbus.controller.ClusterReceiver;
import org.apache.skywalking.apm.testcase.vertxeventbus.controller.VertxEventbusController;

public class Application {

    public static void main(String[] args) {
        System.setProperty("vertx.disableFileCPResolving", "true");
        ClusterManager mgr = new HazelcastClusterManager();
        VertxOptions options = new VertxOptions().setClusterManager(mgr);
        Vertx.clusteredVertx(options, cluster -> {
            if (cluster.succeeded()) {
                cluster.result().deployVerticle(new ClusterReceiver(), deploy -> {
                    if (deploy.succeeded()) {
                        ClusterManager mgr2 = new HazelcastClusterManager();
                        VertxOptions options2 = new VertxOptions().setClusterManager(mgr2);
                        Vertx.clusteredVertx(options2, cluster2 -> {
                            if (cluster2.succeeded()) {
                                cluster2.result().deployVerticle(new VertxEventbusController());
                            } else {
                                cluster2.cause().printStackTrace();
                                System.exit(-1);
                            }
                        });
                    } else {
                        deploy.cause().printStackTrace();
                        System.exit(-1);
                    }
                });
            } else {
                cluster.cause().printStackTrace();
                System.exit(-1);
            }
        });
    }
}
