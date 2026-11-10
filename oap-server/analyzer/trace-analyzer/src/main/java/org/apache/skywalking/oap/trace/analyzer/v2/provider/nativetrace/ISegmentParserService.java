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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Where the segments SkyWalking agents send are analyzed: the native trace receiver, the Kafka fetcher and the
 * data-generator tool hand each segment here, and the native rule runner analyzes it.
 */
public interface ISegmentParserService extends Service {
    void send(SegmentObject segment);

}
