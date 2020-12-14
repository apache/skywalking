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

package org.apache.skywalking.oap.server.receiver.envoy.als.wrapper;

import com.google.common.base.Strings;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.receiver.envoy.als.ProtoMessages.findField;

@Accessors(fluent = true)
public class Identifier {
    protected Role role;

    @Getter
    protected Message identifier;

    @Getter
    protected Struct nodeMetadata;

    public Identifier(final Message identifier) {
        this.identifier = identifier;
        this.nodeMetadata = findField(identifier, "node.metadata", null);
        this.identify(Strings.nullToEmpty(findField(identifier, "node.id", "")));
    }

    private void identify(final String id) {
        if (id.startsWith("router~")) {
            this.role = Role.PROXY;
        } else if (id.startsWith("sidecar~")) {
            this.role = Role.SIDECAR;
        }
    }

    public Role toRole(final Role defaultRole) {
        return nonNull(role) ? role : defaultRole;
    }
}
