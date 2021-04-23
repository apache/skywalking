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

package org.apache.skywalking.oap.server.receiver.envoy;

import com.google.protobuf.Struct;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.mx.ServiceMetaInfoAdapter;

public class ServiceMetaInfoFactoryImpl implements ServiceMetaInfoFactory {
    @Override
    public Class<? extends ServiceMetaInfo> clazz() {
        return ServiceMetaInfo.class;
    }

    @Override
    public ServiceMetaInfo unknown() {
        return new ServiceMetaInfo("UNKNOWN", "UNKNOWN");
    }

    @Override
    public ServiceMetaInfo fromStruct(final Struct struct) throws Exception {
        return new ServiceMetaInfoAdapter(struct);
    }
}
