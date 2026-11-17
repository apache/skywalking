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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The two passes a log without a layer runs, taken from one view of the rules so that a rule moving
 * between the auto and the fallback auto rules is seen in exactly one of them for the whole log.
 */
@Getter
@RequiredArgsConstructor
public final class AutoListeners {
    /** The auto rules' listener; null when there are none. */
    private final LogAnalysisListener auto;
    /** The fallback auto rules' listener, run when no auto rule claimed the log; null when there are none. */
    private final LogAnalysisListener fallback;
}
