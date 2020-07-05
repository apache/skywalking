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

package org.apache.skywalking.e2e.retryable;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

@RequiredArgsConstructor
final class RetryableTestContext implements TestTemplateInvocationContext {
    private final Class<? extends Throwable> throwable;
    private final int invocation;
    private final int times;

    @Override
    public List<Extension> getAdditionalExtensions() {
        return Collections.singletonList(new OneShotExtension(throwable, invocation, times));
    }

    @Override
    public String getDisplayName(final int invocationIndex) {
        return String.format("test attempt #%d", invocationIndex);
    }
}
