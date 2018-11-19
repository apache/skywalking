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

package org.apache.skywalking.oap.server.core.remote;

import io.grpc.inprocess.*;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.core.worker.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.testing.module.*;
import org.junit.*;

import static org.mockito.Mockito.*;

/**
 * @author peng-yongsheng
 */
public class RemoteServiceHandlerTestCase {

    @Rule
    public final GrpcCleanupRule gRPCCleanup = new GrpcCleanupRule();

    @Test
    public void callTest() throws DuplicateProviderException, ProviderNotFoundException, IOException {
        final int streamDataClassId = 1;
        final int testWorkerId = 1;

        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting moduleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, moduleDefine);

        StreamDataClassGetter classGetter = mock(StreamDataClassGetter.class);
        Class<?> dataClass = TestRemoteData.class;
        when(classGetter.findClassById(streamDataClassId)).thenReturn((Class<StreamData>)dataClass);

        moduleDefine.provider().registerServiceImplementation(StreamDataClassGetter.class, classGetter);

        WorkerInstances.INSTANCES.put(testWorkerId, new TestWorker());

        String serverName = InProcessServerBuilder.generateName();

        gRPCCleanup.register(InProcessServerBuilder
            .forName(serverName).directExecutor().addService(new RemoteServiceHandler(moduleManager)).build().start());

        RemoteServiceGrpc.RemoteServiceStub remoteServiceStub = RemoteServiceGrpc.newStub(
            gRPCCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        StreamObserver<RemoteMessage> streamObserver = remoteServiceStub.call(new StreamObserver<Empty>() {
            @Override public void onNext(Empty empty) {

            }

            @Override public void onError(Throwable throwable) {

            }

            @Override public void onCompleted() {

            }
        });

        RemoteMessage.Builder remoteMessage = RemoteMessage.newBuilder();
        remoteMessage.setStreamDataId(streamDataClassId);
        remoteMessage.setNextWorkerId(testWorkerId);

        RemoteData.Builder remoteData = RemoteData.newBuilder();
        remoteData.addDataStrings("test1");
        remoteData.addDataStrings("test2");

        remoteData.addDataLongs(10);
        remoteData.addDataLongs(20);
        remoteMessage.setRemoteData(remoteData);

        streamObserver.onNext(remoteMessage.build());
        streamObserver.onCompleted();
    }

    static class TestRemoteData extends StreamData {

        private String str1;
        private String str2;
        private long long1;
        private long long2;

        @Override public int remoteHashCode() {
            return 10;
        }

        @Override public void deserialize(RemoteData remoteData) {
            str1 = remoteData.getDataStrings(0);
            str2 = remoteData.getDataStrings(1);
            long1 = remoteData.getDataLongs(0);
            long2 = remoteData.getDataLongs(1);

            Assert.assertEquals("test1", str1);
            Assert.assertEquals("test2", str2);
            Assert.assertEquals(10, long1);
            Assert.assertEquals(20, long2);
        }

        @Override public RemoteData.Builder serialize() {
            return null;
        }
    }

    static class TestWorker extends AbstractWorker {

        public TestWorker() {
            super(1);
        }

        @Override public void in(Object o) {
            TestRemoteData data = (TestRemoteData)o;

            Assert.assertEquals("test1", data.str1);
            Assert.assertEquals("test2", data.str2);
            Assert.assertEquals(10, data.long1);
            Assert.assertEquals(20, data.long2);
        }
    }
}
