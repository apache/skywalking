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

package org.apache.skywalking.oap.server.receiver.jvm.provider;

import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;

/**
 * JVM OAl script includes the metrics related to JVM only.
 */
public class JVMOALDefine extends OALDefine {
    public static final JVMOALDefine INSTANCE = new JVMOALDefine();

    private JVMOALDefine() {
        super(
            "oal/java-agent.oal",
            "org.apache.skywalking.oap.server.core.source"
        );
    }
}
