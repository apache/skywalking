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

package org.apache.skywalking.oal.tool;

import freemarker.template.TemplateException;
import java.io.*;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oal.tool.meta.*;
import org.apache.skywalking.oal.tool.output.FileGenerator;
import org.apache.skywalking.oal.tool.parser.*;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

public class Main {

    public static void main(String[] args) throws IOException, TemplateException {
        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        scopeScan.scan(null);

        String modulePath = args[0];

        String scriptFilePath = StringUtil.join(File.separatorChar, modulePath, "src", "main", "resources", "official_analysis.oal");
        String outputPath = StringUtil.join(File.separatorChar, modulePath, "..", "generated-analysis", "target", "generated-sources", "oal",
            "org", "apache", "skywalking", "oap", "server", "core", "analysis");
        String metaFilePath = StringUtil.join(File.separatorChar, modulePath, "src", "main", "resources", "generator-scope-meta.yml");

        Indicators.init();

        File scriptFile = new File(scriptFilePath);
        if (!scriptFile.exists()) {
            throw new IllegalArgumentException("OAL script file [" + scriptFilePath + "] doesn't exist");
        }

        File metaFile = new File(metaFilePath);
        if (!metaFile.exists()) {
            throw new IllegalArgumentException("Generator meta file [" + metaFilePath + "] doesn't exist");
        }

        MetaReader reader = new MetaReader();
        MetaSettings metaSettings = reader.read(new FileInputStream(metaFile));
        SourceColumnsFactory.setSettings(metaSettings);

        ScriptParser scriptParser = ScriptParser.createFromFile(scriptFilePath);
        OALScripts oalScripts = scriptParser.parse();

        FileGenerator generator = new FileGenerator(oalScripts, outputPath);
        generator.generate();
    }
}
