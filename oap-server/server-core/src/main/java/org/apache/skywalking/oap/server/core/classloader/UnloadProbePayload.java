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

package org.apache.skywalking.oap.server.core.classloader;

/**
 * Bytecode payload for {@link ClassLoaderGc}'s unload-evidence probe. Never instantiated and
 * never used as a class — the probe reads this class's {@code .class} resource and defines a
 * copy of it into a throwaway parent-less classloader, which makes that loader collectible
 * only by a class-unloading-capable GC cycle (the same collection requirement a retired
 * {@link RuleClassLoader} has). Keep it empty: the smaller the bytecode, the cheaper each
 * probe generation.
 */
final class UnloadProbePayload {
    private UnloadProbePayload() {
    }
}
