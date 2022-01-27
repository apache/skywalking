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

import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.skywalking.oap.server.core.Const.DOUBLE_COLONS_SPLIT;

public class Metadata {
    public static class ServiceTrafficBuilder extends BanyanDBStorageDataBuilder<ServiceTraffic> {
        @Override
        protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(ServiceTraffic entity) {
            final String serviceName = entity.getName();
            entity.setShortName(serviceName);
            if (entity.isNormal()) {
                int groupIdx = serviceName.indexOf(DOUBLE_COLONS_SPLIT);
                if (groupIdx > 0) {
                    entity.setGroup(serviceName.substring(0, groupIdx));
                    entity.setShortName(serviceName.substring(groupIdx + 2));
                }
            }
            List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(4);
            // 0 - serviceName
            searchable.add(TagAndValue.stringField(serviceName));
            // 1 - serviceID
            searchable.add(TagAndValue.stringField(entity.getServiceId()));
            // 2 - group
            searchable.add(TagAndValue.stringField(entity.getGroup()));
            // 3 - layer
            Layer layer = entity.getLayer();
            searchable.add(TagAndValue.longField(layer != null ? layer.value() : Layer.UNDEFINED.value()));
            return searchable;
        }

        @Override
        protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(ServiceTraffic entity) {
            // 0 - shortName
            return Collections.singletonList(TagAndValue.stringField(entity.getShortName()));
        }
    }

    public static class EndpointTrafficBuilder extends BanyanDBStorageDataBuilder<EndpointTraffic> {
        @Override
        protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(EndpointTraffic entity) {
            List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(2);
            // 0 - serviceID
            searchable.add(TagAndValue.stringField(entity.getServiceId()));
            // 1 - name
            searchable.add(TagAndValue.stringField(entity.getName()));
            return searchable;
        }
    }

    public static class InstanceTrafficBuilder extends BanyanDBStorageDataBuilder<InstanceTraffic> {
        @Override
        protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(InstanceTraffic entity) {
            List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(3);
            // serviceID
            searchable.add(TagAndValue.stringField(entity.getServiceId()));
            // lastPingTimestamp
            searchable.add(TagAndValue.longField(entity.getLastPingTimestamp()));
            // ID: we have to duplicate "ID" here for query
            searchable.add(TagAndValue.stringField(entity.id()));
            return searchable;
        }

        @Override
        protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(InstanceTraffic entity) {
            return Collections.singletonList(TagAndValue.binaryField(
                    entity.serialize().build().toByteArray()
            ));
        }
    }
}
