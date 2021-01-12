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

package org.apache.skywalking.oap.server.configuration.etcd;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

/**
 * entity wrapps the etcd cluster configuration.
 */
@ToString
@Getter
@Setter
public class EtcdServerSettings extends ModuleConfig {

    private String clusterName = "default";
    /**
     * etcd cluster address, like "10.10.10.1:2379, 10.10.10.2:2379,10.10.10.3.2379".
     */
    private String serverAddr;

    /**
     * directory for configuration
     */
    private String group;

    /**
     * sec for interval refresh config data.
     */
    private int period = 60;

}
