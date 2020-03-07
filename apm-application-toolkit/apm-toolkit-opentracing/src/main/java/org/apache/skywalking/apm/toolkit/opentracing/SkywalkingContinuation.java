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

package org.apache.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;

public class SkywalkingContinuation implements ActiveSpan.Continuation {
    @NeedSnifferActivation("1. ContextManager#capture" + "2. set ContextSnapshot to the dynamic field")
    public SkywalkingContinuation() {
    }

    @NeedSnifferActivation("1. get ContextSnapshot from the dynamic field" + "2. ContextManager#continued")
    @Override
    public ActiveSpan activate() {
        SkywalkingSpanBuilder builder = new SkywalkingSpanBuilder("Thread/" + Thread.currentThread().getName());
        return builder.startActive();
    }
}
