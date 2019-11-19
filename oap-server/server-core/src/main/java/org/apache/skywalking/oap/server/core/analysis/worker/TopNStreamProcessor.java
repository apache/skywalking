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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.analysis.*;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * TopN is a special process, which hold a certain size of windows, and cache all top N records, save to the persistence
 * in low frequency.
 *
 * @author wusheng
 */
public class TopNStreamProcessor implements StreamProcessor<TopN> {

    private static final TopNStreamProcessor PROCESSOR = new TopNStreamProcessor();

    @Getter private List<TopNWorker> persistentWorkers = new ArrayList<>();
    private Map<Class<? extends Record>, TopNWorker> workers = new HashMap<>();
    @Setter @Getter private int topNWorkerReportCycle = 10;
    @Setter @Getter private int topSize = 50;

    public static TopNStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @SuppressWarnings("unchecked")
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends TopN> topNClass) {
        if (DisableRegister.INSTANCE.include(stream.name())) {
            return;
        }

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRecordDAO recordDAO;
        try {
            recordDAO = storageDAO.newRecordDao(stream.builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder().getSimpleName() + " top n record DAO failure.", e);
        }

        IModelSetter modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(IModelSetter.class);
        Model model = modelSetter.putIfAbsent(topNClass, stream.scopeId(), new Storage(stream.name(), true, true, Downsampling.Second), true);

        TopNWorker persistentWorker = new TopNWorker(moduleDefineHolder, model, topSize, topNWorkerReportCycle * 60 * 1000L, recordDAO);
        persistentWorkers.add(persistentWorker);
        workers.put(topNClass, persistentWorker);
    }

    public void in(TopN topN) {
        TopNWorker worker = workers.get(topN.getClass());
        if (worker != null) {
            worker.in(topN);
        }
    }
}
