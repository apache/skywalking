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

package org.apache.skywalking.oap.server.admin.inspect;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Inspect feature module — admin-only.
 *
 * <p>Hosts {@code GET /inspect/metrics} (the metric catalog) and
 * {@code GET /inspect/entities} (entities with values for a metric in a time
 * range, decoded into MQE-ready form). The handlers register on
 * admin-server's HTTP register; there is no public-REST mirror because
 * inspect's entity-enumeration scan competes with user-facing query traffic
 * for storage and is intentionally gated behind the private admin port.
 */
public class InspectModule extends ModuleDefine {
    public static final String NAME = "inspect";

    public InspectModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[0];
    }
}
