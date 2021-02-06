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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ZabbixConfigs {

    public static List<ZabbixConfig> loadConfigs(String path, List<String> fileNames) throws ModuleStartException {
        if (CollectionUtils.isEmpty(fileNames)) {
            return Collections.emptyList();
        }

        File[] configs;
        try {
            configs = ResourceUtils.getPathFiles(path);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Load zabbix configs failed", e);
        }

        return Arrays.stream(configs).filter(File::isFile)
            .map(f -> {
                String fileName = f.getName();
                int dotIndex = fileName.lastIndexOf('.');
                fileName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
                if (!fileNames.contains(fileName)) {
                    return null;
                }
                try (Reader r = new FileReader(f)) {
                    return new Yaml().loadAs(r, ZabbixConfig.class);
                } catch (IOException e) {
                    log.warn("Reading file {} failed", f, e);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
