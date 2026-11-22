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

package org.apache.skywalking.oap.server.core.analysis;

import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisableRegisterTest {

    @Stream(name = "disable_register_test_stream", scopeId = DefaultScopeDefine.SEGMENT,
        builder = SegmentRecord.Builder.class, processor = RecordStreamProcessor.class)
    private static class DisabledStream {
    }

    @Test
    public void aScriptDisableNamingNoStreamIsReported() {
        // A register of its own: the process-wide one would carry the unknown name into a later boot in this JVM.
        final DisableRegister register = new DisableRegister();
        register.addFromScript("typo");
        register.addFromScript("defined_later");
        register.markStreamDefined("defined_later");
        assertTrue(register.include("defined_later"));
        assertTrue(register.scriptDisablesWithoutStream().contains("typo"));
        assertFalse(register.scriptDisablesWithoutStream().contains("defined_later"));
    }

    @Test
    public void aStreamRunsUnlessDisabledBeforeItIsDefined() throws Exception {
        final DisableRegister register = new DisableRegister();
        register.markStreamDefined("defined_first");
        assertTrue(register.isStreamRunning("defined_first"));
        register.addFromScript("disabled_first");
        register.markStreamDefined("disabled_first");
        assertFalse(register.isStreamRunning("disabled_first"));

        // The listener marks a stream defined even when it is disabled, and returns before it needs the module
        // manager. Disabled and defined, the name leaves nothing unknown in the process-wide register.
        DisableRegister.INSTANCE.addFromScript("disable_register_test_stream");
        new StreamAnnotationListener(null).notify(DisabledStream.class);
        assertFalse(DisableRegister.INSTANCE.isStreamRunning("disable_register_test_stream"));
        assertFalse(DisableRegister.INSTANCE.scriptDisablesWithoutStream().contains("disable_register_test_stream"));
    }
}
