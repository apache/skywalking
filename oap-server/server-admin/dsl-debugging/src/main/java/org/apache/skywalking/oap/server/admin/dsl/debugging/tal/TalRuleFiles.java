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

package org.apache.skywalking.oap.server.admin.dsl.debugging.tal;

import java.io.StringReader;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * The TAL rule files as this node loaded them: the bytes {@code RuleSetMerger} recorded in {@link StaticRuleRegistry}
 * at boot under the {@code tal} catalog, which the native image records the same way.
 */
@Slf4j
final class TalRuleFiles {
    static final String CATALOG = "tal";

    private TalRuleFiles() {
    }

    /**
     * @param name the name the loader filed the rule file under, without its extension
     * @return the file's content as loaded; empty when this node loaded no such file
     */
    static Optional<String> content(final String name) {
        return StaticRuleRegistry.active().find(CATALOG, name);
    }

    /**
     * The generated probes number a rule's lines within its {@code dsl:} value; the debugger shows lines of the file.
     *
     * @return the file line of the rule's first DSL line; 0 when the file or the rule is not found
     */
    static int dslFirstLine(final String yaml, final String ruleName) {
        if (yaml == null || ruleName == null) {
            return 0;
        }
        try {
            final Node root = new Yaml(new LoaderOptions()).compose(new StringReader(yaml));
            if (!(root instanceof MappingNode)) {
                return 0;
            }
            for (final NodeTuple section : ((MappingNode) root).getValue()) {
                if ("rules".equals(scalar(section.getKeyNode()))
                    && section.getValueNode() instanceof SequenceNode) {
                    return dslLineOf((SequenceNode) section.getValueNode(), ruleName);
                }
            }
        } catch (final RuntimeException e) {
            log.debug("Could not index the TAL rule file for rule {}: {}", ruleName, e.getMessage());
        }
        return 0;
    }

    private static int dslLineOf(final SequenceNode rules, final String ruleName) {
        for (final Node item : rules.getValue()) {
            if (!(item instanceof MappingNode)) {
                continue;
            }
            Node dsl = null;
            boolean named = false;
            for (final NodeTuple field : ((MappingNode) item).getValue()) {
                final String key = scalar(field.getKeyNode());
                if ("name".equals(key)) {
                    named = ruleName.equals(scalar(field.getValueNode()));
                } else if ("dsl".equals(key)) {
                    dsl = field.getValueNode();
                }
            }
            if (named && dsl instanceof ScalarNode) {
                // A literal block, `dsl: |`, keeps its lines and starts on the line after its indicator; a value on
                // one line that reads as one line is that line. Any other style folds its line breaks, or makes them
                // of escapes, so its lines are not the file's, and the steps keep their lines within the DSL.
                final int startLine = dsl.getStartMark().getLine() + 1;
                if (((ScalarNode) dsl).getScalarStyle() == DumperOptions.ScalarStyle.LITERAL) {
                    return startLine + 1;
                }
                final boolean oneLine = dsl.getEndMark().getLine() + 1 == startLine
                    && ((ScalarNode) dsl).getValue().trim().indexOf('\n') < 0;
                return oneLine ? startLine : 0;
            }
        }
        return 0;
    }

    private static String scalar(final Node node) {
        return node instanceof ScalarNode ? ((ScalarNode) node).getValue() : null;
    }
}
