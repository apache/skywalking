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

package org.apache.skywalking.apm.plugin.zookeeper;

import java.util.HashMap;
import java.util.Map;
import org.apache.jute.Record;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.proto.CheckVersionRequest;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.proto.DeleteRequest;
import org.apache.zookeeper.proto.ExistsRequest;
import org.apache.zookeeper.proto.GetACLRequest;
import org.apache.zookeeper.proto.GetChildren2Request;
import org.apache.zookeeper.proto.GetChildrenRequest;
import org.apache.zookeeper.proto.GetDataRequest;
import org.apache.zookeeper.proto.GetMaxChildrenRequest;
import org.apache.zookeeper.proto.SetACLRequest;
import org.apache.zookeeper.proto.SetDataRequest;
import org.apache.zookeeper.proto.SetMaxChildrenRequest;
import org.apache.zookeeper.proto.SyncRequest;

class ZooOpt {

    private static final Map<Integer, String> OPTS = new HashMap<>();
    private static final AbstractTag<String> PATH = Tags.ofKey("path");
    private static final AbstractTag<String> VERSION = Tags.ofKey("version");
    private static final AbstractTag<String> WATCH = Tags.ofKey("watch");
    private static final AbstractTag<String> MAX_CHILDREN = Tags.ofKey("max");
    private static final AbstractTag<String> KEEPER_STATE = Tags.ofKey("state");

    static {
        OPTS.put(ZooDefs.OpCode.notification, "notification");
        OPTS.put(ZooDefs.OpCode.create, "create");
        OPTS.put(ZooDefs.OpCode.delete, "delete");
        OPTS.put(ZooDefs.OpCode.exists, "exists");
        OPTS.put(ZooDefs.OpCode.getData, "getData");
        OPTS.put(ZooDefs.OpCode.setData, "setData");
        OPTS.put(ZooDefs.OpCode.getACL, "getACL");
        OPTS.put(ZooDefs.OpCode.setACL, "setACL");
        OPTS.put(ZooDefs.OpCode.getChildren, "getChildren");
        OPTS.put(ZooDefs.OpCode.sync, "sync");
        OPTS.put(ZooDefs.OpCode.ping, "ping");
        OPTS.put(ZooDefs.OpCode.getChildren2, "getChildren2");
        OPTS.put(ZooDefs.OpCode.check, "check");
        OPTS.put(ZooDefs.OpCode.multi, "multi");
        OPTS.put(ZooDefs.OpCode.auth, "auth");
        OPTS.put(ZooDefs.OpCode.setWatches, "setWatches");
        OPTS.put(ZooDefs.OpCode.sasl, "sasl");
        OPTS.put(ZooDefs.OpCode.createSession, "createSession");
        OPTS.put(ZooDefs.OpCode.closeSession, "closeSession");
        OPTS.put(ZooDefs.OpCode.error, "error");
    }

    static String getOperationName(Integer opCode) {
        String operationName = OPTS.get(opCode);
        return operationName == null ? "unknown" : operationName;
    }

    /**
     * Add the tag attribute only for the implementation of the Request suffix except ConnectRequest.class because no
     * very important attributes except GetSASLRequest.class because no very important attributes except
     * SetSASLRequest.class because no very important attributes
     *
     * @param span   SkyWalking AbstractSpan.class
     * @param record Zookeeper Record.class
     */
    static void setTags(AbstractSpan span, Record record) {
        if (record instanceof CheckVersionRequest) {
            CheckVersionRequest recordImpl = (CheckVersionRequest) record;
            span.tag(PATH, recordImpl.getPath());
        } else if (record instanceof CreateRequest) {
            CreateRequest recordImpl = (CreateRequest) record;
            span.tag(PATH, recordImpl.getPath());
        } else if (record instanceof DeleteRequest) {
            DeleteRequest recordImpl = (DeleteRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(VERSION, String.valueOf(recordImpl.getVersion()));
        } else if (record instanceof ExistsRequest) {
            ExistsRequest recordImpl = (ExistsRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(WATCH, String.valueOf(recordImpl.getWatch()));
        } else if (record instanceof GetACLRequest) {
            GetACLRequest recordImpl = (GetACLRequest) record;
            span.tag(PATH, recordImpl.getPath());
        } else if (record instanceof GetChildren2Request) {
            GetChildren2Request recordImpl = (GetChildren2Request) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(WATCH, String.valueOf(recordImpl.getWatch()));
        } else if (record instanceof GetChildrenRequest) {
            GetChildrenRequest recordImpl = (GetChildrenRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(WATCH, String.valueOf(recordImpl.getWatch()));
        } else if (record instanceof GetDataRequest) {
            GetDataRequest recordImpl = (GetDataRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(WATCH, String.valueOf(recordImpl.getWatch()));
        } else if (record instanceof GetMaxChildrenRequest) {
            GetMaxChildrenRequest recordImpl = (GetMaxChildrenRequest) record;
            span.tag(PATH, recordImpl.getPath());
        } else if (record instanceof SetACLRequest) {
            SetACLRequest recordImpl = (SetACLRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(VERSION, String.valueOf(recordImpl.getVersion()));
        } else if (record instanceof SetDataRequest) {
            SetDataRequest recordImpl = (SetDataRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(VERSION, String.valueOf(recordImpl.getVersion()));
        } else if (record instanceof SetMaxChildrenRequest) {
            SetMaxChildrenRequest recordImpl = (SetMaxChildrenRequest) record;
            span.tag(PATH, recordImpl.getPath());
            span.tag(MAX_CHILDREN, String.valueOf(recordImpl.getMax()));
        } else if (record instanceof SyncRequest) {
            SyncRequest recordImpl = (SyncRequest) record;
            span.tag(PATH, recordImpl.getPath());
        }
    }

    /**
     * Add the necessary tags for the WatchedEvent
     *
     * @param span  SkyWalking AbstractSpan.class
     * @param event Zookeeper WatchedEvent.class
     */
    static void setTags(AbstractSpan span, WatchedEvent event) {
        span.tag(PATH, event.getPath());
        span.tag(KEEPER_STATE, event.getState().name());
    }
}
