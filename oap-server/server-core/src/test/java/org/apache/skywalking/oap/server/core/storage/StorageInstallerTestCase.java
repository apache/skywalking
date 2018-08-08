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

package org.apache.skywalking.oap.server.core.storage;

import java.util.LinkedList;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataAnnotationContainer;
import org.apache.skywalking.oap.server.core.storage.model.*;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.*;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

/**
 * @author peng-yongsheng
 */
public class StorageInstallerTestCase {

    @Test
    public void testInstall() throws StorageException, ServiceNotProvidedException {
        StreamDataAnnotationContainer streamDataAnnotationContainer = new StreamDataAnnotationContainer();
        CoreModuleProvider moduleProvider = Mockito.mock(CoreModuleProvider.class);
        CoreModule moduleDefine = Mockito.spy(CoreModule.class);
        ModuleManager moduleManager = Mockito.mock(ModuleManager.class);

        LinkedList<ModuleProvider> moduleProviders = Whitebox.getInternalState(moduleDefine, "loadedProviders");
        moduleProviders.add(moduleProvider);

        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleDefine);
        Mockito.when(moduleProvider.getService(StreamDataAnnotationContainer.class)).thenReturn(streamDataAnnotationContainer);

//        streamDataAnnotationContainer.generate();

//        TestStorageInstaller installer = new TestStorageInstaller(moduleManager);
//        installer.install(null);
    }

    class TestStorageInstaller extends ModelInstaller {

        public TestStorageInstaller(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override protected boolean isExists(Client client, Model tableDefine) throws StorageException {
            return false;
        }

        @Override protected void columnCheck(Client client, Model tableDefine) throws StorageException {

        }

        @Override protected void deleteTable(Client client, Model tableDefine) throws StorageException {

        }

        @Override protected void createTable(Client client, Model tableDefine) throws StorageException {

        }
    }
}
