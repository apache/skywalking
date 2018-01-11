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

package org.apache.skywalking.apm.collector.analysis.register.define.graph;

/**
 * @author peng-yongsheng
 */
public class WorkerIdDefine {
    public static final int APPLICATION_REGISTER_REMOTE_WORKER = 200;
    public static final int APPLICATION_REGISTER_SERIAL_WORKER = 201;
    public static final int INSTANCE_REGISTER_REMOTE_WORKER = 202;
    public static final int INSTANCE_REGISTER_SERIAL_WORKER = 203;
    public static final int SERVICE_NAME_REGISTER_REMOTE_WORKER = 204;
    public static final int SERVICE_NAME_REGISTER_SERIAL_WORKER = 205;
    public static final int NETWORK_ADDRESS_REGISTER_REMOTE_WORKER = 206;
    public static final int NETWORK_ADDRESS_REGISTER_SERIAL_WORKER = 207;
}
