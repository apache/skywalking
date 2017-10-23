/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class RemoteWorkerProviderDefineLoader implements Loader<List<AbstractRemoteWorkerProvider>> {

    private final Logger logger = LoggerFactory.getLogger(RemoteWorkerProviderDefineLoader.class);

    @Override public List<AbstractRemoteWorkerProvider> load() throws DefineException {
        List<AbstractRemoteWorkerProvider> providers = new ArrayList<>();
        RemoteWorkerProviderDefinitionFile definitionFile = new RemoteWorkerProviderDefinitionFile();
        logger.info("remote worker provider definition file name: {}", definitionFile.fileName());

        DefinitionLoader<AbstractRemoteWorkerProvider> definitionLoader = DefinitionLoader.load(AbstractRemoteWorkerProvider.class, definitionFile);

        for (AbstractRemoteWorkerProvider provider : definitionLoader) {
            logger.info("loaded remote worker provider definition class: {}", provider.getClass().getName());
            providers.add(provider);
        }
        return providers;
    }
}
