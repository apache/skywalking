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

package org.apache.skywalking.oap.server.receiver.runtimerule.apply;

import java.util.ArrayList;
import java.util.List;
import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfigs;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A runtime rule is stamped with the same source coordinates as the same file loaded at boot: the applier's
 * {@code catalog/name} source must not leave the catalog in the name, or the attribution reads {@code lal/lal/...}.
 */
class LalFileApplierSourceTest {
    private static final String YAML =
        "rules:\n"
            + "  - name: default\n"
            + "    layer: GENERAL\n"
            + "    dsl: |\n"
            + "      filter {\n"
            + "        sink {\n"
            + "        }\n"
            + "      }\n";

    @Test
    void aRuntimeRuleIsStampedAsItsBootLoadedTwin() throws Exception {
        final List<LALConfig> compiled = new ArrayList<>();
        final LogFilterListener.Factory factory = Mockito.mock(LogFilterListener.Factory.class);
        when(factory.compile(any(LALConfig.class), any(ClassPool.class), any(ClassLoader.class)))
            .thenAnswer(inv -> {
                final LALConfig c = inv.getArgument(0);
                compiled.add(c);
                return new LogFilterListener.Factory.CompiledLAL(Layer.nameOf(c.getLayer()), c.getName(), null);
            });

        new LalFileApplier(factory).apply(YAML, "lal/default", "h");

        final LALConfig bootLoaded = new LALConfig();
        LALConfigs.stampSource(bootLoaded, "default.yaml");
        assertEquals("lal/default.yaml", compiled.get(0).getSourcePath());
        assertEquals(bootLoaded.getSourcePath(), compiled.get(0).getSourcePath());
        assertEquals(bootLoaded.getSourceName(), compiled.get(0).getSourceName());
    }
}
