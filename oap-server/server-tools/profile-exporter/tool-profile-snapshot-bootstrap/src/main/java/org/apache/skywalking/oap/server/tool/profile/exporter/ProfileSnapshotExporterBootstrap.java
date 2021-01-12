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

package org.apache.skywalking.oap.server.tool.profile.exporter;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.starter.config.ApplicationConfigLoader;

import java.io.File;

@Slf4j
public class ProfileSnapshotExporterBootstrap {
    public static void export(String[] args) {
        ApplicationConfigLoader configLoader = new ApplicationConfigLoader();
        ModuleManager manager = new ModuleManager();
        try {
            // parse config and init
            ExporterConfig exporterConfig = ExporterConfig.parse(args);
            exporterConfig.init();

            // init OAP
            ApplicationConfiguration applicationConfiguration = configLoader.load();
            manager.init(applicationConfiguration);

            // prepare basic info
            ProfiledBasicInfo profiledBaseInfo = ProfiledBasicInfo.build(exporterConfig, manager);
            log.info("Queried profiled basic info, profiled segment start time:{}, duration:{}, total span count:{}, snapshot count:{}",
                    profiledBaseInfo.getSegmentStartTime(), profiledBaseInfo.getDuration(), profiledBaseInfo.getProfiledSegmentSpans().size(),
                    profiledBaseInfo.getMaxSequence() - profiledBaseInfo.getMinSequence());

            // write basic info to file
            File basicInfoFile = profiledBaseInfo.writeFile();
            log.info("Write segment info to file:{}", basicInfoFile.getAbsolutePath());

            // query and writing snapshot
            File snapshotFile = ProfileSnapshotDumper.dump(profiledBaseInfo, manager);
            log.info("Write snapshot to file:{}", snapshotFile);

            // exit program
            System.exit(0);
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            System.exit(1);
        }
    }
}
