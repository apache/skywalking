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
 */

package org.apache.skywalking.oap.server.core.alarm;

import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordPersistentWorker;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.DuplicateProviderException;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.ModuleNotFoundRuntimeException;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ProviderNotFoundException;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jsbxyyx
 */
public class AlarmStandardPersistenceTest {

    @Test
    public void doAlarmNPEVerify() throws Exception {
        AlarmStandardPersistence persistence = new AlarmStandardPersistence();
        try {
            persistence.doAlarm(null);
        } catch (NullPointerException e) {
            Assert.assertFalse(false);
        }
    }

    @Test
    public void doAlarmVerify() throws Exception {
        List<AlarmMessage> alarmMessageList = new ArrayList<>();
        final AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessageList.add(alarmMessage);
        mockRecordStreamProcessor(new Callback() {
            @Override
            public void call(Object... arguments) {
                AlarmRecord record = (AlarmRecord) arguments[1];
                Assert.assertEquals(alarmMessage.getScopeId(), record.getScope());
                Assert.assertEquals(alarmMessage.getId0(), record.getId0());
                Assert.assertEquals(alarmMessage.getId1(), record.getId1());
                Assert.assertEquals(alarmMessage.getName(), record.getName());
                Assert.assertEquals(alarmMessage.getAlarmMessage(), record.getAlarmMessage());
                Assert.assertEquals(alarmMessage.getStartTime(), record.getStartTime());
                Assert.assertEquals(TimeBucket.getRecordTimeBucket(alarmMessage.getStartTime()), record.getTimeBucket());
            }
        });

        AlarmStandardPersistence persistence = new AlarmStandardPersistence();
        persistence.doAlarm(alarmMessageList);
    }

    private void mockRecordStreamProcessor(final Callback recordDaoCallback) throws Exception {
        ModuleDefineHolder holder = new ModuleDefineHolder() {
            @Override
            public boolean has(String moduleName) {
                return false;
            }

            @Override
            public ModuleProviderHolder find(String moduleName) throws ModuleNotFoundRuntimeException {
                return new ModuleProviderHolder() {
                    @Override
                    public ModuleServiceHolder provider() throws DuplicateProviderException, ProviderNotFoundException {
                        return new ModuleServiceHolder() {
                            @Override
                            public void registerServiceImplementation(Class<? extends Service> serviceType, Service service) throws ServiceNotProvidedException {

                            }

                            @Override
                            public <T extends Service> T getService(Class<T> serviceType) throws ServiceNotProvidedException {
                                return (T) new IBatchDAO() {
                                    @Override
                                    public void asynchronous(InsertRequest insertRequest) {

                                    }

                                    @Override
                                    public void synchronous(List<PrepareRequest> prepareRequests) {

                                    }
                                };
                            }
                        };
                    }
                };
            }
        };

        Model model = new Model("name", new ArrayList<>(),
                true, true, 0, Downsampling.None, true);

        IRecordDAO dao = new IRecordDAO() {
            @Override
            public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
                recordDaoCallback.call(model, record);
                return null;
            }
        };

        Map<Class<? extends Record>, RecordPersistentWorker> workers = new HashMap<>();
        Constructor<RecordPersistentWorker> constructor = RecordPersistentWorker.class.getDeclaredConstructor(
                ModuleDefineHolder.class, Model.class, IRecordDAO.class);
        constructor.setAccessible(true);
        RecordPersistentWorker worker = constructor.newInstance(holder, model, dao);
        workers.put(Record.class, worker);

        RecordStreamProcessor instance = RecordStreamProcessor.getInstance();
        Field workers1 = instance.getClass().getDeclaredField("workers");
        workers1.setAccessible(true);
        workers1.set(instance, workers);
    }

    private interface Callback {

        void call(Object... arguments);

    }

}
