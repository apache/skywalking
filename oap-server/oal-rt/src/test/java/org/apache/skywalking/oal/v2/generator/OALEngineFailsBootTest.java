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

package org.apache.skywalking.oal.v2.generator;

import org.apache.skywalking.oal.v2.OALEngineV2;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine the OAP boots through throws for a script that does not compile, naming its line and, for a rule check,
 * its metric. {@code OALEngineLoaderService#load} lets the exception through, as each module's {@code start()} does,
 * and {@code OAPServerBootstrap} exits on it: the OAP does not boot.
 */
public class OALEngineFailsBootTest {
    @BeforeAll
    public static void registerScopes() {
        // Registers the scopes and decorators the scripts use.
        OALScriptRunner.classPool();
    }

    private static Exception start(String script) {
        // A catalog of its own keeps its dispatcher apart from the ones other tests define.
        final OALEngineV2 engine = new OALEngineV2(new OALDefine("checks-boot/" + script,
                                                                 "org.apache.skywalking.oap.server.core.source",
                                                                 "ChecksBoot") {
        });
        engine.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        return assertThrows(Exception.class, () -> engine.start(OALEngineFailsBootTest.class.getClassLoader()));
    }

    @Test
    public void aRefusedFormStopsTheBoot() {
        final String message = start("refused.oal").getMessage();
        assertTrue(message.contains("checks-boot/refused.oal:20:") && message.contains("chk_boot_refused")
                       && message.contains("a like pattern starts or ends with %"), message);
    }

    @Test
    public void aSyntaxErrorStopsTheBoot() {
        final String message = start("syntax.oal").getMessage();
        assertTrue(message.contains("checks-boot/syntax.oal:19:"), message);
    }

    @Test
    public void aClassTheVerifierRejectsStopsTheBoot() {
        final String message = start("unlinkable.oal").getMessage();
        assertTrue(message.contains("checks-boot/unlinkable.oal:19:") && message.contains("chk_boot_link"), message);
    }
}
