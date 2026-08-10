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

package org.apache.skywalking.oap.server.core.dsl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic naming for generated DSL classes, shared by OAL, MAL, LAL and Hierarchy.
 *
 * <p>The stem is {@code {sanitizedRuleFile}_L{line}_{hint}}, e.g.
 * {@code otel_rules_vm_L38_cpu_total}. Each generator used to build that itself, along with its own
 * identifier sanitiser and its own dedup set, so the {@code _Lunknown_} policy for an unresolved
 * line held in one of them and not the others: MAL rendered {@code _Lunknown_}, while LAL and
 * Hierarchy omitted the segment entirely or appended a raw {@code 0}.
 *
 * <p><b>Naming and allocation are separate on purpose.</b> The stem is deterministic and shared.
 * Whether a colliding name gets a {@code _2} suffix is a loader question, and the answer genuinely
 * differs: MAL and LAL give each runtime-rule apply its own classloader, so identical names land in
 * different namespaces and process-wide dedup would grow without bound; Hierarchy always defines
 * into the shared loader and must dedup.
 */
public final class DslClassNaming {

    private static final Set<String> USED_CLASS_NAMES =
        Collections.synchronizedSet(new HashSet<>());

    private DslClassNaming() {
    }

    
    /**
     * The deterministic stem for a generated class.
     *
     * <p>The rule file's extension is dropped before sanitising, so {@code vm.yaml} contributes
     * {@code vm} rather than {@code vm_yaml}.
     *
     * @param ref  the rule's coordinate; a null file yields the hint alone
     * @param hint rule name, or {@code filter} for a filter class
     * @return e.g. {@code otel_rules_vm_L38_cpu_total}
     */
    public static String stem(final DslSourceRef ref, final String hint) {
        return stem(ref, hint, null);
    }

    /**
     * The deterministic stem, with a catalog segment the caller's package already implies dropped.
     *
     * @param ref            the rule's coordinate; a null file yields the hint alone
     * @param hint           rule name, or {@code filter} for a filter class
     * @param impliedCatalog catalog prefix, trailing {@code /} included, that the generated class's
     *                       PACKAGE already identifies, so repeating it in the name says nothing;
     *                       null keeps the whole path. Only a DSL with exactly ONE catalog may
     *                       declare one — MAL's catalogs share a single {@code rt} package and two
     *                       of them ship a {@code vm.yaml}, so there the segment is what keeps the
     *                       names apart.
     * @return e.g. {@code otel_rules_vm_L38_cpu_total}, or {@code default_L3_default} for LAL
     */
    public static String stem(final DslSourceRef ref, final String hint,
                              final String impliedCatalog) {
        final String sanitizedHint = DslJavaSourceText.toIdentifier(hint);
        if (ref == null || ref.getYamlFile() == null) {
            return sanitizedHint;
        }
        String base = ref.getYamlFile();
        if (impliedCatalog != null && base.startsWith(impliedCatalog)) {
            base = base.substring(impliedCatalog.length());
        }
        final int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return DslJavaSourceText.toIdentifier(base) + ref.classNameSegment() + sanitizedHint;
    }

    /**
     * Applies the allocation policy to a stem.
     *
     * @param fqcn  fully-qualified candidate name
     * @param dedup false when the caller gives each apply its own classloader, so identical names
     *              are already isolated and a process-wide set would only grow
     * @return the name to use, with a {@code _2}, {@code _3}… suffix on collision when deduping
     */
    public static String allocate(final String fqcn, final boolean dedup) {
        if (!dedup) {
            return fqcn;
        }
        if (USED_CLASS_NAMES.add(fqcn)) {
            return fqcn;
        }
        for (int i = 2; ; i++) {
            final String candidate = fqcn + "_" + i;
            if (USED_CLASS_NAMES.add(candidate)) {
                return candidate;
            }
        }
    }

    }
