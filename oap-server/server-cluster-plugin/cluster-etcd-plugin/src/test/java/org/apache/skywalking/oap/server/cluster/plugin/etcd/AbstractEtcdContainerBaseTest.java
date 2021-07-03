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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import com.google.common.collect.Lists;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractEtcdContainerBaseTest {
    static final GenericContainer CONTAINER = new GenericContainer(DockerImageName.parse("bitnami/etcd:3.5.0"));


    static {
        CONTAINER.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*etcd setup finished!.*"));
        CONTAINER.withReuse(false);
        CONTAINER.setEnv(Lists.newArrayList("ALLOW_NONE_AUTHENTICATION=yes"));
        CONTAINER.start();
    }
}
