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

package org.apache.skywalking.oap.server.admin.status;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Status feature module — replaces the legacy status-query-plugin.
 *
 * <p>Hosts the cluster / alarm / TTL status endpoints plus the per-query
 * debugging trace endpoints. URI paths and response payloads are
 * preserved verbatim from the legacy plugin.
 *
 * <p>Mounts on the admin-server REST host. {@code /status/config/ttl}
 * is additionally bound on the public REST host (kept for
 * baseline-predictor, which fetches TTL bounds before issuing
 * /graphql); every other handler is admin-only.
 */
public class StatusModule extends ModuleDefine {
    public static final String NAME = "status";

    public StatusModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {AlarmStatusQueryService.class};
    }
}
