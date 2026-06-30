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

package org.apache.skywalking.oap.server.core.status;

import java.util.Map;

/**
 * Contributes additional effective (post-environment-resolution) configurations to the
 * {@code /debugging/config/dump} output. It exists for modules whose runtime configuration is
 * loaded from a secondary file outside {@code application.yml} — for example the BanyanDB storage
 * plugin loads {@code bydb.yml} / {@code bydb-topn.yml} into its own POJO, which the boot-time
 * {@link ServerStatusService#dumpBootingConfigurations(String)} cannot otherwise see.
 *
 * <p>Implementations register themselves through
 * {@link ServerStatusService#registerConfigDumpExtension(ConfigDumpExtension)}.
 *
 * @since 11.0.0
 */
public interface ConfigDumpExtension {
    /**
     * @return the effective configurations as fully-qualified {@code module.provider.key} to value
     * pairs, keyed exactly like the boot dump so they interleave with it. Values are returned raw;
     * the dump masks secrets centrally by key, so each key should carry its field name as the last
     * segment (e.g. {@code storage.banyandb.global.password}).
     */
    Map<String, String> dumpConfigurations();
}
