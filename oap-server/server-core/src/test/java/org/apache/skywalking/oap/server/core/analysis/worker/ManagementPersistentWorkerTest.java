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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ManagementPersistentWorkerTest {

    @Test
    void inSwallowsIoExceptionForAsyncCallers() throws IOException {
        // Async path — in() catches IOException and only logs. UITemplate / UIMenu callers
        // accept this fire-and-forget contract; their DAOs swallow duplicate-row writes
        // anyway. Runtime-rule moved off this path because it requires persist-is-commit
        // semantics; that contract now lives on RuntimeRuleManagementDAO.save instead.
        final ModuleDefineHolder holder = mock(ModuleDefineHolder.class);
        final IManagementDAO dao = mock(IManagementDAO.class);
        final Model model = mock(Model.class);
        final ManagementPersistentWorker worker =
            new ManagementPersistentWorker(holder, model, dao);
        final ManagementData data = mock(ManagementData.class);
        doThrow(new IOException("db unavailable")).when(dao).insert(any(), any());

        // Should NOT throw — caller that uses in() explicitly accepts fire-and-forget
        // semantics; assertion is just "does not propagate the IOException".
        worker.in(data);

        // Verify the DAO call did happen, so we know we exercised the swallow path rather
        // than short-circuiting before insert.
        verify(dao).insert(model, data);
    }
}
