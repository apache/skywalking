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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.schema;

import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Metadata {
    public static class ServiceTrafficBuilder extends BanyanDBMetricsBuilder<ServiceTraffic> {
        @Override
        protected List<SerializableTag<Banyandb.TagValue>> searchableTags(ServiceTraffic entity) {
            List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(3);
            searchable.add(TagAndValue.stringField(entity.getName()));
            searchable.add(TagAndValue.longField(entity.getNodeType().value()));
            searchable.add(TagAndValue.stringField(entity.getGroup()));
            return searchable;
        }
    }

    public static class EndpointTrafficBuilder extends BanyanDBMetricsBuilder<EndpointTraffic> {
        @Override
        protected List<SerializableTag<Banyandb.TagValue>> searchableTags(EndpointTraffic entity) {
            List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(2);
            searchable.add(TagAndValue.stringField(entity.getServiceId()));
            searchable.add(TagAndValue.stringField(entity.getName()));
            return searchable;
        }
    }

    public static class InstanceTrafficBuilder extends BanyanDBMetricsBuilder<InstanceTraffic> {
        @Override
        protected List<SerializableTag<Banyandb.TagValue>> searchableTags(InstanceTraffic entity) {
            List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(2);
            searchable.add(TagAndValue.stringField(entity.getServiceId()));
            searchable.add(TagAndValue.longField(entity.getLastPingTimestamp()));
            return searchable;
        }

        @Override
        protected List<SerializableTag<Banyandb.TagValue>> dataTags(InstanceTraffic entity) {
            return Collections.singletonList(TagAndValue.binaryField(
                    entity.serialize().build().toByteArray()
            ));
        }
    }
}
