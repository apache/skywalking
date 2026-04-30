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

package org.apache.skywalking.oap.server.core.analysis;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.List;
import java.util.ServiceLoader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads external {@link Layer} registrations from two sources during
 * {@code CoreModuleProvider.prepare()}:
 *
 * <ol>
 *   <li>{@code layer-extensions.yml} on the OAP classpath — operator-deployed config.
 *       Optional file; absence is not an error.</li>
 *   <li>{@link LayerExtension} implementations discovered via {@code ServiceLoader} —
 *       out-of-tree plugin jars on the OAP classpath.</li>
 * </ol>
 *
 * <p>Both sources funnel through {@link Layer#register(String, int, boolean)};
 * conflict checks (reserved-range, name-uniqueness, ordinal-uniqueness, name-shape,
 * sealed-state) live there.
 *
 * <p>MAL/LAL DSL files declaring inline {@code layerDefinitions:} blocks are
 * <strong>not</strong> handled here — they're processed by their respective DSL loaders
 * during the meter-receiver / log-analyzer modules' {@code prepare()}, which runs after
 * Core's {@code prepare()} but before the registry is sealed in
 * {@code Core.notifyAfterCompleted()}.
 *
 * <p>Expected yaml schema (all entries optional, file may be empty):
 * <pre>{@code
 * layers:
 *   - name: IOT_FLEET     # upper-snake-case, must match [A-Z][A-Z0-9_]*
 *     ordinal: 1000       # >= 1000 (0-999 reserved for OAP distribution)
 *     normal: true        # true = agent-installed, false = conjectured/virtual
 * }</pre>
 */
@Slf4j
public final class LayerExtensionLoader {

    private static final String LAYER_EXTENSIONS_FILE = "layer-extensions.yml";

    private LayerExtensionLoader() {
    }

    /**
     * Loads yaml first, then SPI. Each entry is registered immediately so collisions
     * are reported with their actual source (yaml line or SPI class) in the stack trace.
     * Logs the (name, ordinal) table contributed by these two sources at INFO; later
     * additions from MAL/LAL inline {@code layerDefinitions:} blocks are not yet present
     * at this point and will appear in subsequent boot logs as those rule files load.
     */
    public static void load() {
        loadFromYaml();
        loadFromSpi();
        log.info("Layer registry after yaml + SPI extension loading: {}", Layer.describeRegistry());
    }

    private static void loadFromYaml() {
        final Reader reader;
        try {
            reader = ResourceUtils.read(LAYER_EXTENSIONS_FILE);
        } catch (FileNotFoundException e) {
            log.debug("{} not found on classpath; no operator-defined layer extensions.", LAYER_EXTENSIONS_FILE);
            return;
        }
        final LayerExtensionsFile root = new Yaml().loadAs(reader, LayerExtensionsFile.class);
        if (root == null || root.getLayers() == null) {
            return;
        }
        for (final LayerDefinition def : root.getLayers()) {
            def.register();
        }
    }

    /** SnakeYAML target for {@code layer-extensions.yml}'s root document. */
    @Data
    public static class LayerExtensionsFile {
        private List<LayerDefinition> layers;
    }

    private static void loadFromSpi() {
        for (final LayerExtension extension : ServiceLoader.load(LayerExtension.class)) {
            log.info("Applying LayerExtension SPI: {}", extension.getClass().getName());
            extension.contribute();
        }
    }
}
