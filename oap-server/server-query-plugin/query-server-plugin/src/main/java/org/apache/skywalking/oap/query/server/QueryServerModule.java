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

package org.apache.skywalking.oap.query.server;

import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * The HTTP server of the native query APIs: GraphQL, the AI agent conversation view and files, and the TTL
 * lookup. Without a port of its own these stay on the core REST server; with one they move to it, so the query
 * APIs can be bound, firewalled and given TLS apart from the ports agents report to.
 */
public class QueryServerModule extends ModuleDefine {

    public static final String NAME = "query-server";

    public QueryServerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            HTTPHandlerRegister.class
        };
    }
}
