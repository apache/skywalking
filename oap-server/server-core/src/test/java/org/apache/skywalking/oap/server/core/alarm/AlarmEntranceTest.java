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

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.library.module.DuplicateProviderException;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleNotFoundRuntimeException;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ProviderNotFoundException;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * @author jsbxyyx
 */
public class AlarmEntranceTest {


    @Test
    public void forwardVerifyDoNotInitMethod() throws Exception {
        ModuleDefineHolder holder = mockModuleDefineHolder(false);
        AlarmEntrance alarmEntrance = new AlarmEntrance(holder);
        alarmEntrance.forward(new AlarmEntranceMetrics());
        Field metricsNotify = alarmEntrance.getClass().getDeclaredField("metricsNotify");
        metricsNotify.setAccessible(true);
        Object o = metricsNotify.get(alarmEntrance);
        Assert.assertEquals(null, o);
    }


    @Test
    public void forwardVerifyDoInitMethod() throws Exception {
        ModuleDefineHolder holder = mockModuleDefineHolder(true);
        AlarmEntrance alarmEntrance = new AlarmEntrance(holder);
        alarmEntrance.forward(new AlarmEntranceMetrics());
        Field metricsNotify = alarmEntrance.getClass().getDeclaredField("metricsNotify");
        metricsNotify.setAccessible(true);
        Object o = metricsNotify.get(alarmEntrance);
        Assert.assertEquals(true, o != null);
    }


    private ModuleDefineHolder mockModuleDefineHolder(boolean has) {
        return new AlarmEntranceModuleDefineHolder(has);
    }

    private static class AlarmEntranceModuleDefineHolder implements ModuleDefineHolder {

        private boolean has;

        public AlarmEntranceModuleDefineHolder(boolean has) {
            this.has = has;
        }

        @Override
        public boolean has(String moduleName) {
            return has;
        }

        @Override
        public ModuleProviderHolder find(String moduleName) throws ModuleNotFoundRuntimeException {
            ModuleProviderHolder holder = new ModuleProviderHolder() {
                @Override
                public ModuleServiceHolder provider() throws DuplicateProviderException, ProviderNotFoundException {
                    ModuleServiceHolder holder1 = new ModuleServiceHolder() {
                        @Override
                        public void registerServiceImplementation(Class<? extends Service> serviceType, Service service) throws ServiceNotProvidedException {

                        }

                        @Override
                        public <T extends Service> T getService(Class<T> serviceType) throws ServiceNotProvidedException {
                            return (T) new MetricsNotify() {
                                @Override
                                public void notify(Metrics metrics) {

                                }
                            };
                        }
                    };
                    return holder1;
                }
            };
            return holder;
        }
    }

    private static class AlarmEntranceMetrics extends Metrics {

        @Override
        public String id() {
            return null;
        }

        @Override
        public void combine(Metrics metrics) {

        }

        @Override
        public void calculate() {

        }

        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        public Metrics toMonth() {
            return null;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }

}
