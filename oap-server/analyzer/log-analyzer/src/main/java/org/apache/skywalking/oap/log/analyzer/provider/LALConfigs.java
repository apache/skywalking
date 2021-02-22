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

package org.apache.skywalking.oap.log.analyzer.provider;

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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Files.getNameWithoutExtension;
import static org.apache.skywalking.apm.util.StringUtil.isNotBlank;
import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isEmpty;

@Data
@Slf4j
public class LALConfigs {
    private List<LALConfig> rules;

    public static List<LALConfigs> load(final String path, final List<String> files) throws Exception {
        if (isEmpty(files)) {
            return Collections.emptyList();
        }

        checkArgument(isNotBlank(path), "path cannot be blank");

        try {
            final File[] rules = ResourceUtils.getPathFiles(path);

            return Arrays.stream(rules)
                         .filter(File::isFile)
                         .filter(it -> {
                             //noinspection UnstableApiUsage
                             return files.contains(getNameWithoutExtension(it.getName()));
                         })
                         .map(f -> {
                             try (final Reader r = new FileReader(f)) {
                                 return new Yaml().loadAs(r, LALConfigs.class);
                             } catch (IOException e) {
                                 log.debug("Failed to read file {}", f, e);
                             }
                             return null;
                         })
                         .filter(Objects::nonNull)
                         .collect(Collectors.toList());
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Failed to load LAL config rules", e);
        }
    }
}
