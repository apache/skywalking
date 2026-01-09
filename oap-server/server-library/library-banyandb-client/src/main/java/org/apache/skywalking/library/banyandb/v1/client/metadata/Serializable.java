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

package org.apache.skywalking.library.banyandb.v1.client.metadata;

/**
 * Serializable provide serialize method to build a protobuf message.
 * This is normally for inner types of metadata.
 *
 * @param <P> the produced class must be in Protobuf message type.
 */
public interface Serializable<P extends com.google.protobuf.GeneratedMessageV3> {
    /**
     * Serialize the object to the protobuf format
     *
     * @return protobuf representation
     */
    P serialize();
}
