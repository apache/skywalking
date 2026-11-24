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

package org.apache.skywalking.oap.server.admin.dsl.debugging.rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.skywalking.oap.server.admin.dsl.debugging.module.DSLDebuggingModuleConfig;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status names the catalogs a session can be installed on, so a client can tell which DSLs an OAP debugs.
 */
class DSLDebuggingRestHandlerStatusTest {

    @Test
    void theStatusListsTheCatalogsASessionCanBeInstalledOn() {
        final DSLDebuggingRestHandler handler = new DSLDebuggingRestHandler(
            new DSLDebuggingModuleConfig(), new DebugSessionRegistry(), null, "self");

        final JsonObject status = JsonParser.parseString(handler.status().aggregate().join().contentUtf8())
                                            .getAsJsonObject();

        final String catalogs = status.getAsJsonArray("catalogs").toString();
        assertTrue(catalogs.contains("\"tal\"") && catalogs.contains("\"lal\"") && catalogs.contains("\"oal\""),
            catalogs);
    }
}
