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

package org.apache.skywalking.oap.server.admin.trace.pairing;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * An on-demand capture of the network-alias pairing of OTLP calls, one per node: the halves the node that owns a
 * trace receives, which of them match, and the alias each match pushes. Not a rule's run, so it is apart from the DSL
 * debugging.
 */
public class TracePairingDebuggingModule extends ModuleDefine {
    public static final String NAME = "trace-pairing-debugging";

    public TracePairingDebuggingModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[0];
    }
}
