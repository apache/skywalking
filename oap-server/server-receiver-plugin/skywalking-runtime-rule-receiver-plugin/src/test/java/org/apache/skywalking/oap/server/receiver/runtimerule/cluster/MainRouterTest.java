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

package org.apache.skywalking.oap.server.receiver.runtimerule.cluster;

import java.util.Collections;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainRouterTest {

    @Test
    void selfIsMainWhenPeerListEmpty() {
        // Empty peer list reflects either "no cluster module wired" (rcm == null) or a
        // refresh window where the manager momentarily has no entries. Either way the
        // local node is the operator's authority for runtime-rule writes. The earlier
        // {@code isPeerListReady} guard that 503'd writes during cold-boot is gone —
        // the cluster routing layer now treats empty list and null rcm symmetrically as
        // "self is main", so writes accept without an extra readiness gate.
        assertTrue(MainRouter.isSelfMain(null));
        final RemoteClientManager emptyRcm = mock(RemoteClientManager.class);
        when(emptyRcm.getRemoteClient()).thenReturn(Collections.emptyList());
        assertTrue(MainRouter.isSelfMain(emptyRcm));
    }
}
