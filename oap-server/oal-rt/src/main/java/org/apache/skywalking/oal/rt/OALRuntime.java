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

package org.apache.skywalking.oal.rt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import org.apache.skywalking.oal.rt.meta.MetaReader;
import org.apache.skywalking.oal.rt.meta.MetaSettings;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.parser.SourceColumnsFactory;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

/**
 * OAL Runtime is the class generation engine, which load the generated classes from OAL scrip definitions.
 *
 * @author wusheng
 */
public class OALRuntime implements OALEngine {
    @Override public void start(ClassLoader currentClassLoader) throws ModuleStartException {
        Reader read;
        try {
            read = ResourceUtils.read("scope-meta.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate scope-meta.yml", e);
        }

        MetaReader reader = new MetaReader();
        MetaSettings metaSettings = reader.read(read);
        SourceColumnsFactory.setSettings(metaSettings);

        try {
            read = ResourceUtils.read("official_analysis.oal");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate official_analysis.oal", e);
        }

        ScriptParser scriptParser = null;
        try {
            scriptParser = ScriptParser.createFromFile(read);
            OALScripts oalScripts = scriptParser.parse();
        } catch (IOException e) {
            throw new ModuleStartException("OAL script parse analysis failure.", e);
        }

    }
}
