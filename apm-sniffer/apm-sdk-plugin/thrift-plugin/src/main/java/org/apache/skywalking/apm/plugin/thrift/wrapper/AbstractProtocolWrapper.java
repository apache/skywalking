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

package org.apache.skywalking.apm.plugin.thrift.wrapper;

import java.util.Map;
import java.util.Set;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;

public abstract class AbstractProtocolWrapper extends TProtocolDecorator {
    public static final String SW_MAGIC_FIELD = "SW_MAGIC_FIELD";
    public static final short SW_MAGIC_FIELD_ID = 8888; // A magic number

    public AbstractProtocolWrapper(final TProtocol protocol) {
        super(protocol);
    }

    protected void writeHeader(Map<String, String> header) throws TException {
        super.writeFieldBegin(new TField(SW_MAGIC_FIELD, TType.MAP, SW_MAGIC_FIELD_ID));
        super.writeMapBegin(new TMap(TType.STRING, TType.STRING, header.size()));

        Set<Map.Entry<String, String>> entries = header.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            super.writeString(entry.getKey());
            super.writeString(entry.getValue());
        }

        super.writeMapEnd();
        super.writeFieldEnd();
    }
}
