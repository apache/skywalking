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
public class LocalAsyncWorkerProviderDefineLoader implements Loader<List<AbstractLocalAsyncWorkerProvider>> {

    private final Logger logger = LoggerFactory.getLogger(LocalAsyncWorkerProviderDefineLoader.class);

    @Override public List<AbstractLocalAsyncWorkerProvider> load() throws DefineException {
        List<AbstractLocalAsyncWorkerProvider> providers = new ArrayList<>();
        LocalWorkerProviderDefinitionFile definitionFile = new LocalWorkerProviderDefinitionFile();
        logger.info("local async worker provider definition file name: {}", definitionFile.fileName());

        DefinitionLoader<AbstractLocalAsyncWorkerProvider> definitionLoader = DefinitionLoader.load(AbstractLocalAsyncWorkerProvider.class, definitionFile);

        for (AbstractLocalAsyncWorkerProvider provider : definitionLoader) {
            logger.info("loaded local async worker provider definition class: {}", provider.getClass().getName());
            providers.add(provider);
        }
        return providers;
    }
}
