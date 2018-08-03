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

import java.io.*;
import java.net.URL;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.library.module.Service;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class IndicatorMapper implements Service {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorMapper.class);

    private int id = 0;
    private final Map<Class<Indicator>, Integer> classKeyMapping;
    private final Map<Integer, Class<Indicator>> idKeyMapping;

    public IndicatorMapper() {
        this.classKeyMapping = new HashMap<>();
        this.idKeyMapping = new HashMap<>();
    }

    @SuppressWarnings(value = "unchecked")
    public void load() throws IndicatorDefineLoadException {
        try {
            List<String> indicatorClasses = new LinkedList<>();

            Enumeration<URL> urlEnumeration = this.getClass().getClassLoader().getResources("META-INF/defines/indicator.def");
            while (urlEnumeration.hasMoreElements()) {
                URL definitionFileURL = urlEnumeration.nextElement();
                logger.info("Load indicator definition file url: {}", definitionFileURL.getPath());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(definitionFileURL.openStream()));
                Properties properties = new Properties();
                properties.load(bufferedReader);

                Enumeration defineItem = properties.propertyNames();
                while (defineItem.hasMoreElements()) {
                    String fullNameClass = (String)defineItem.nextElement();
                    indicatorClasses.add(fullNameClass);
                }
            }

            for (String indicatorClassName : indicatorClasses) {
                Class<Indicator> indicatorClass = (Class<Indicator>)Class.forName(indicatorClassName);
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

    public Collection<Class<Indicator>> indicatorClasses() {
        return idKeyMapping.values();
    }
}
