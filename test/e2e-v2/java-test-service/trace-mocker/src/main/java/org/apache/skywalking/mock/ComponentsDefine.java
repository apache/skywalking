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

package org.apache.skywalking.mock;

/**
 * The supported list of skywalking java sniffer.
 */
public class ComponentsDefine {

    public static final OfficialComponent TOMCAT = new OfficialComponent(1, "Tomcat");

    public static final OfficialComponent DUBBO = new OfficialComponent(3, "Dubbo");

    public static final OfficialComponent ROCKET_MQ_PRODUCER = new OfficialComponent(38, "rocketMQ-producer");

    public static final OfficialComponent ROCKET_MQ_CONSUMER = new OfficialComponent(39, "rocketMQ-consumer");

    public static final OfficialComponent MONGO_DRIVER = new OfficialComponent(42, "mongodb-driver");
}
