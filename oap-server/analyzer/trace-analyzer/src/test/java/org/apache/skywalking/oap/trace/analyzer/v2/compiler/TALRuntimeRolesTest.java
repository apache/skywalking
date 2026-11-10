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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalConfigFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalGeneralFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalValueConversions;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime is broken into roles: generated code reaches a role by a method name only one role declares, and each
 * input's provider hands the kernel the helper functions its rules call, each serving the inputs it accepts.
 */
class TALRuntimeRolesTest {
    @Test
    void generatedCodeReachesTheRoleThatDeclaresTheMethod() {
        assertEquals(TalValueConversions.class.getName() + ".toInt", TALRuntimeRoles.method("toInt"));
        assertThrows(IllegalArgumentException.class, () -> TALRuntimeRoles.method("noSuchMethod"));
    }

    @Test
    void aMethodNameBelongsToOneRole() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> TALRuntimeRoles.index(List.of(RoleA.class, RoleB.class)));
        assertTrue(e.getMessage().contains("convert is declared by both"), e.getMessage());
    }

    @Test
    void theShippedInputsHandOverTheSharedHelpers() {
        for (final List<TALFunctionProvider> helpers : List.of(new OtlpTALSourceTypeProvider().helperFunctions(),
            new SwNativeTALSourceTypeProvider().helperFunctions())) {
            assertEquals(2, helpers.size());
            assertInstanceOf(TalGeneralFunctions.class, helpers.get(0));
            assertInstanceOf(TalConfigFunctions.class, helpers.get(1));
        }
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        registry.bind(null, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        for (final String input : Set.of("otlp", "sw-native")) {
            assertEquals(TalGeneralFunctions.class,
                registry.candidates("urlHost", 1, input).get(0).getOwner().getClass());
            assertEquals(TalConfigFunctions.class,
                registry.candidates("isGateway", 1, input).get(0).getOwner().getClass());
        }
    }

    @Test
    void aHelperMustServeTheInputThatHandsItOver() {
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> registry.bind(null, List.of(new OtlpWithNativeOnlyHelper())));
        assertTrue(e.getMessage().contains("hands over the helper functions of " + NativeOnlyHelper.class.getName()),
            e.getMessage());
    }

    @Test
    void aProviderFunctionMayNotTakeAHelpersName() {
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> registry.bind(null, List.of(new OtlpWithOwnUrlHost())));
        assertTrue(e.getMessage().contains("urlHost is served by both"), e.getMessage());
    }

    @Test
    void eachHelperAProviderHandsOverIsBoundOnce() {
        final CountingHelper helper = new CountingHelper();
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        // The OTLP provider's helpers also serve an unregistered input; still one bind.
        registry.bind(null, List.of(new OtlpWithCountingHelper(helper)));
        assertEquals(1, helper.binds);
    }

    public static final class CountingHelper implements TALFunctionProvider {
        private int binds;

        @Override
        public String module() {
            return "trace-analyzer";
        }

        @Override
        public boolean supportsInputType(final String inputType) {
            return true;
        }

        @Override
        public void bind(final ModuleManager moduleManager) {
            binds++;
        }

        public String counted() {
            return "c";
        }
    }

    public static final class OtlpWithCountingHelper extends OtlpTALSourceTypeProvider {
        private final CountingHelper helper;

        public OtlpWithCountingHelper(final CountingHelper helper) {
            this.helper = helper;
        }

        @Override
        public List<TALFunctionProvider> helperFunctions() {
            return List.of(helper);
        }
    }

    public static final class RoleA {
        public static int convert(final int value) {
            return value;
        }
    }

    public static final class RoleB {
        public static String convert(final String value) {
            return value;
        }
    }

    public static final class NativeOnlyHelper implements TALFunctionProvider {
        @Override
        public String module() {
            return "trace-analyzer";
        }

        @Override
        public boolean supportsInputType(final String inputType) {
            return "sw-native".equals(inputType);
        }

        public String nativeHelper() {
            return "n";
        }
    }

    public static final class OtlpWithNativeOnlyHelper extends OtlpTALSourceTypeProvider {
        @Override
        public List<TALFunctionProvider> helperFunctions() {
            return List.of(new NativeOnlyHelper());
        }
    }

    public static final class OtlpWithOwnUrlHost extends OtlpTALSourceTypeProvider {
        public String urlHost(final TalRuntimeHelper h, final String url) {
            return url;
        }
    }
}
