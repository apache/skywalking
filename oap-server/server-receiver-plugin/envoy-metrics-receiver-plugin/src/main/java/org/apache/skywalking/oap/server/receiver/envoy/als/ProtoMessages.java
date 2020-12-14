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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.common.base.Splitter;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Optional;

/**
 * Helper class that provides some utility functions to manipulate Protobuf Messages.
 */
public final class ProtoMessages {
    public static <E> Optional<E> findField(final Message root, final String fieldPath) {
        return Optional.ofNullable(findField(root, fieldPath, null));
    }

    @SuppressWarnings("unchecked")
    public static <E> E findField(final Message root, final String fieldPath, final E defaultVal) {
        final List<String> fields = Splitter.on(".").splitToList(fieldPath);

        Message msg = root;
        for (int i = 0; i < fields.size(); i++) {
            final String field = fields.get(i);
            final Descriptors.FieldDescriptor fieldDescriptor = msg.getDescriptorForType().findFieldByName(field);
            final Object obj = msg.getField(fieldDescriptor);
            if (obj == null) {
                return defaultVal;
            }
            if (i == fields.size() - 1) {
                return (E) obj;
            } else {
                msg = (Message) obj;
            }
        }
        return defaultVal;
    }
}
