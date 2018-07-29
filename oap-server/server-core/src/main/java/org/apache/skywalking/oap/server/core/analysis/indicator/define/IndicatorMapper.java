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

package org.apache.skywalking.oap.server.core.analysis.indicator.define;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * @author peng-yongsheng
 */
public class IndicatorMapper implements Service {

    private int id = 0;
    private final Map<Class<Indicator>, Integer> classKeyMapping;
    private final Map<Integer, Class<Indicator>> idKeyMapping;

    public IndicatorMapper() {
        this.classKeyMapping = new HashMap<>();
        this.idKeyMapping = new HashMap<>();
    }

    @SuppressWarnings(value = "unchecked")
    public void load() throws IndicatorDefineLoadException {
        URL url = Resources.getResource("META-INF/defines/indicator.def");

        try {
            List<String> lines = Resources.readLines(url, Charsets.UTF_8);

            for (String line : lines) {
                Class<Indicator> indicatorClass = (Class<Indicator>)Class.forName(line);
                id++;
                classKeyMapping.put(indicatorClass, id);
                idKeyMapping.put(id, indicatorClass);
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IndicatorDefineLoadException(e.getMessage(), e);
        }
    }

    public int findIdByClass(Class indicatorClass) {
        return classKeyMapping.get(indicatorClass);
    }

    public Class<Indicator> findClassById(int id) {
        return idKeyMapping.get(id);
    }
}
