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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import com.google.protobuf.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LalInputDebugRenderer;

/**
 * Indexes {@link LalInputDebugRenderer} SPI implementations by their declared
 * input type. Discovery is pull-based via {@link ServiceLoader} (the same
 * mechanism the {@code LALSourceTypeProvider} seam uses), so a receiver
 * plugin's renderer is found on first use without any registration call.
 */
final class LalInputDebugRenderers {

    private static final Map<Class<?>, LalInputDebugRenderer> BY_TYPE = load();

    private LalInputDebugRenderers() {
    }

    private static Map<Class<?>, LalInputDebugRenderer> load() {
        final Map<Class<?>, LalInputDebugRenderer> map = new HashMap<>();
        for (final LalInputDebugRenderer renderer : ServiceLoader.load(LalInputDebugRenderer.class)) {
            map.put(renderer.inputType(), renderer);
        }
        return map;
    }

    /**
     * Render {@code msg} via a registered renderer, or {@code null} when no
     * renderer is registered for its type or the renderer declined.
     */
    static String render(final Message msg) {
        final LalInputDebugRenderer renderer = BY_TYPE.get(msg.getClass());
        return renderer == null ? null : renderer.render(msg);
    }
}
