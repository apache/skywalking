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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

/**
 * Marker for the artifact that flows phase-to-phase through a {@link RuleEngine}'s
 * lifecycle. {@link RuleEngine#compile} produces a CompiledDSL; the scheduler holds
 * the reference and passes it to {@code fireSchemaChanges} → {@code verify} →
 * {@code commit} (or {@code rollback}). The scheduler treats it as opaque — only the
 * producing engine reads its DSL-specific contents (added / removed / shape-break sets,
 * Applied artifact, per-file classloader, etc.).
 *
 * <p>The contract on this interface is just enough metadata for the scheduler to make
 * routing + bookkeeping decisions; everything the engine itself needs lives on its own
 * subclass.
 */
public interface CompiledDSL {
    String getCatalog();

    String getName();

    /** SHA-256 hex of the new content this bundle was compiled from. */
    String getContentHash();

    /** Outcome of {@link RuleEngine#classify} that produced this bundle. */
    Classification getClassification();
}
