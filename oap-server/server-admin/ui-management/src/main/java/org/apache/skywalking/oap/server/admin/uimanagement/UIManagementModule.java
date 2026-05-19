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

package org.apache.skywalking.oap.server.admin.uimanagement;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * UI Management feature module — admin-only.
 *
 * <p>Hosts the REST surface used by the web UI for dashboard templates:
 * <ul>
 *   <li>{@code GET    /ui-management/templates?includingDisabled=&lt;bool&gt;}</li>
 *   <li>{@code GET    /ui-management/templates/{id}}</li>
 *   <li>{@code POST   /ui-management/templates}             (add — body: configuration)</li>
 *   <li>{@code PUT    /ui-management/templates}             (change — body: id + configuration)</li>
 *   <li>{@code POST   /ui-management/templates/{id}/disable}</li>
 * </ul>
 *
 * <p>The sidebar menu is intentionally NOT served from OAP in 11.0.0+:
 * Horizon UI ships its own menu config in its bundle and computes
 * "layer has services" gating client-side via {@code listServices(layer:...)}
 * on the metadata query surface.
 *
 * <p>Handlers register on admin-server's HTTP register; there is no public
 * REST mirror — write operations are operator-only and gated behind the admin
 * gateway (same posture as runtime-rule and dsl-debugging).
 */
public class UIManagementModule extends ModuleDefine {
    public static final String NAME = "ui-management";

    public UIManagementModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[0];
    }
}
