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

package org.apache.skywalking.oap.server.receiver.meter.provider.config;

import lombok.Data;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MeterConfigs {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeterConfigs.class);

    public static List<MeterConfig> loadConfig(String path) throws ModuleStartException {
        File[] configs;
        try {
            configs = ResourceUtils.getPathFiles(path);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Load meter configs failed", e);
        }

        return Arrays.stream(configs)
            .map(f -> {
                try (Reader r = new FileReader(f)) {
                    return new Yaml().loadAs(r, Config.class);
                } catch (IOException e) {
                    LOGGER.warn("Reading file {} failed", f, e);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .flatMap(c -> c.getMeters().stream())
            .collect(Collectors.toList());
    }

    @Data
    public static class Config {
        private List<MeterConfig> meters;
    }
}
