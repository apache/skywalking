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

package org.apache.skywalking.library.banyandb.v1.client;

import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;

public class TopNQueryResponse {
    @Getter
    private final List<TopNList> topNLists;

    TopNQueryResponse(BanyandbMeasure.TopNResponse response) {
        final List<BanyandbMeasure.TopNList> timelines = response.getListsList();
        topNLists = new ArrayList<>(timelines.size());
        for (final BanyandbMeasure.TopNList topNList : timelines) {
            topNLists.add(new TopNList(topNList.getTimestamp(), topNList.getItemsList()));
        }
    }

    public int size() {
        return this.topNLists == null ? 0 : this.topNLists.size();
    }

    @Getter
    public static class TopNList {
        /**
         * timestamp of the entity in the timeunit of milliseconds.
         */
        private final long timestamp;
        private final List<Item> items;

        private TopNList(Timestamp ts, List<BanyandbMeasure.TopNList.Item> itemsList) {
            this.timestamp = ts.getSeconds() * 1000 + ts.getNanos() / 1_000_000;
            this.items = new ArrayList<>(itemsList.size());
            for (final BanyandbMeasure.TopNList.Item item : itemsList) {
                this.items.add(Item.parseFrom(item));
            }
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class Item {
        private final Map<String, TagAndValue<?>> tagValuesMap;
        private final Object value;

        static Item parseFrom(BanyandbMeasure.TopNList.Item item) {
            final Object fieldValue = DataPoint.convertFileValueToJavaType(item.getValue());
            final Map<String, TagAndValue<?>> map = new HashMap<>(item.getEntityCount());
            for (final BanyandbModel.Tag tag : item.getEntityList()) {
                map.put(tag.getKey(), TagAndValue.fromProtobuf(tag));
            }
            return new Item(map, fieldValue);
        }
    }
}
