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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import Wasm.Common.FlatNode;
import Wasm.Common.KeyVal;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

/**
 * Adapter to {@link ServiceMetaInfo} from various of other datastructures.
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceMetaInfoAdapter extends ServiceMetaInfo {

    /**
     * Try to adapt a {@link ByteString} to {@link ServiceMetaInfo} instance.
     *
     * @param bv the {@link ByteString byte string} to adapt from.
     * @throws Exception if the {@link ByteString byte string} can not be adapted to a {@link ServiceMetaInfo}.
     */
    public ServiceMetaInfoAdapter(final ByteString bv) throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(BytesValue.parseFrom(bv).getValue().toByteArray());
        final FlatNode flatNode = FlatNode.getRootAsFlatNode(buffer);
        if (log.isDebugEnabled()) {
            for (int i = 0; i < flatNode.labelsLength(); i++) {
                final KeyVal kv = flatNode.labels(i);
                if (nonNull(kv)) {
                    log.debug("wasm label: {} : {}", kv.key(), kv.value());
                }
            }
        }

        final Struct metadata = requireNonNull(extractStructFromNodeFlatBuffer(flatNode));
        FieldsHelper.SINGLETON.inflate(metadata, this);
    }

    /**
     * The same functionality with {@link ServiceMetaInfoAdapter#ServiceMetaInfoAdapter(com.google.protobuf.ByteString)}.
     *
     * @param any {@link Any any object} to adapt from.
     * @throws Exception if the {@link Any any object} can not be adapted to a {@link ServiceMetaInfo}.
     */
    public ServiceMetaInfoAdapter(final Any any) throws Exception {
        this(any.getValue());
    }

    /**
     * This method does the reverse conversion of https://github.com/istio/proxy/blob/938a9485a4286f0ce824b76df221a9bb6c8a6989/extensions/common/proto_util.cc#L112. It extracts the metadata from the
     * {@link FlatNode flat buffer node} so that we can reuse the logic of {@link FieldsHelper}.
     *
     * @param node the flat buffer node where to extract the metadata
     * @return the metadata {@link Struct}
     */
    public static Struct extractStructFromNodeFlatBuffer(final FlatNode node) {
        final Struct.Builder builder = Struct.newBuilder();

        builder.putFields("NAME", Value.newBuilder().setStringValue(nullToEmpty(node.name())).build());
        builder.putFields("NAMESPACE", Value.newBuilder().setStringValue(nullToEmpty(node.namespace())).build());
        builder.putFields("CLUSTER_ID", Value.newBuilder().setStringValue(nullToEmpty(node.clusterId())).build());

        final Struct.Builder labels = Struct.newBuilder();
        for (int i = 0; i < node.labelsLength(); i++) {
            final KeyVal label = node.labels(i);
            labels.putFields(nullToEmpty(label.key()), Value.newBuilder().setStringValue(nullToEmpty(label.value())).build());
        }
        builder.putFields("LABELS", Value.newBuilder().setStructValue(labels).build());

        return builder.build();
    }

    /**
     * The same functionality with {@link ServiceMetaInfoAdapter#ServiceMetaInfoAdapter(com.google.protobuf.ByteString)}.
     *
     * @param metadata the {@link Struct struct} to adapt from.
     */
    public ServiceMetaInfoAdapter(final Struct metadata) {
        FieldsHelper.SINGLETON.inflate(requireNonNull(metadata), this);
    }

}
