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


package org.apache.skywalking.apm.agent.core.context;

/**
 * The <code>Injectable</code> represents a provider, which gives the reference of {@link ContextCarrier} and peer
 * for the agent core, for cross-process propagation.
 *
 * @author wusheng
 */
public interface Injectable {
    ContextCarrier getCarrier();

    /**
     * @return peer, represent ipv4, ipv6, hostname, or cluster addresses list.
     */
    String getPeer();
}
