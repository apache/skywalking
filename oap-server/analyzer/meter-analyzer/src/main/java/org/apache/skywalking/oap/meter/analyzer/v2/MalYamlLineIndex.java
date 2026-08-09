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

package org.apache.skywalking.oap.meter.analyzer.v2;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Resolves the 1-based YAML line of each source fragment in a MAL rule file.
 *
 * <p>SnakeYAML's bean binding ({@code Yaml.loadAs}) discards positional {@code Mark} data, so the
 * rule objects the loaders hand to {@link MetricConvert} carry no idea where they came from. This
 * class runs a second {@code compose} pass over the SAME text and reads the marks off the node
 * tree, which is why callers must give it the exact bytes they bound from — re-reading the file
 * could pick up a different revision.
 *
 * <p>Four anchors are resolved, because a compiled MAL expression is a splice of up to three
 * separate source locations plus a separately-compiled filter:
 * <ul>
 *   <li>{@code filter} / {@code expPrefix} / {@code expSuffix} — file-level, one line each.</li>
 *   <li>per {@code metricsRules} entry — the entry's own line (the {@code - name:} anchor, used
 *       for the generated class label) and its {@code exp:} line.</li>
 * </ul>
 *
 * <p>Every accessor returns {@code 0} for "unknown", which every consumer treats as "omit the line"
 * rather than "line zero". Resolution is best-effort by design: a malformed or unusual document
 * degrades to zeros instead of failing the rule load, because a missing line number must never
 * stop a rule from compiling.
 */
@Slf4j
public final class MalYamlLineIndex {

    /** Rules key in the standard MAL rule file. Zabbix uses {@code metrics} instead. */
    public static final String DEFAULT_RULES_KEY = "metricsRules";

    private static final MalYamlLineIndex EMPTY =
        new MalYamlLineIndex(0, 0, 0, Collections.emptyList());

    @Getter
    private final int filterLine;
    @Getter
    private final int expPrefixLine;
    @Getter
    private final int expSuffixLine;
    private final List<RuleLines> rules;

    private MalYamlLineIndex(final int filterLine, final int expPrefixLine,
                             final int expSuffixLine, final List<RuleLines> rules) {
        this.filterLine = filterLine;
        this.expPrefixLine = expPrefixLine;
        this.expSuffixLine = expSuffixLine;
        this.rules = rules;
    }

    /** Per-{@code metricsRules}-entry anchors. */
    @Getter
    public static final class RuleLines {
        /** Line of the entry itself — the {@code - name:} anchor. */
        private final int entryLine;
        /** Line of the entry's {@code exp:} key. */
        private final int expLine;

        RuleLines(final int entryLine, final int expLine) {
            this.entryLine = entryLine;
            this.expLine = expLine;
        }
    }

    /** All-zero index, for callers with no YAML text to inspect. */
    public static MalYamlLineIndex empty() {
        return EMPTY;
    }

    public static MalYamlLineIndex index(final String yamlContent) {
        return index(yamlContent, DEFAULT_RULES_KEY);
    }

    /**
     * @param yamlContent the exact text the rule object was bound from
     * @param rulesKey    key holding the rules sequence — {@link #DEFAULT_RULES_KEY} for standard
     *                    MAL files, {@code metrics} for zabbix
     * @return resolved anchors, or an all-zero index when the document can't be inspected
     */
    public static MalYamlLineIndex index(final String yamlContent, final String rulesKey) {
        if (yamlContent == null || yamlContent.isEmpty()) {
            return EMPTY;
        }
        try (StringReader reader = new StringReader(yamlContent)) {
            final Node root = new Yaml(new LoaderOptions()).compose(reader);
            if (!(root instanceof MappingNode)) {
                return EMPTY;
            }
            int filter = 0;
            int prefix = 0;
            int suffix = 0;
            List<RuleLines> ruleLines = Collections.emptyList();
            for (final NodeTuple tuple : ((MappingNode) root).getValue()) {
                final String key = scalarKey(tuple.getKeyNode());
                if (key == null) {
                    continue;
                }
                switch (key) {
                    case "filter":
                        filter = lineOf(tuple.getKeyNode());
                        break;
                    case "expPrefix":
                        prefix = lineOf(tuple.getKeyNode());
                        break;
                    case "expSuffix":
                        suffix = lineOf(tuple.getKeyNode());
                        break;
                    default:
                        if (key.equals(rulesKey)) {
                            ruleLines = indexRules(tuple.getValueNode());
                        }
                        break;
                }
            }
            return new MalYamlLineIndex(filter, prefix, suffix, ruleLines);
        } catch (final RuntimeException e) {
            // Malformed YAML is the loader's problem to report, not ours — it will surface a far
            // better message than we could. Degrade to "no lines known".
            log.debug("MAL YAML line index unavailable: {}", e.getMessage());
            return EMPTY;
        }
    }

    /** Anchors for the rules entry at {@code index}, or an all-zero entry when out of range. */
    public RuleLines rule(final int index) {
        if (index < 0 || index >= rules.size()) {
            return new RuleLines(0, 0);
        }
        return rules.get(index);
    }

    private static List<RuleLines> indexRules(final Node rulesNode) {
        if (!(rulesNode instanceof SequenceNode)) {
            return Collections.emptyList();
        }
        final List<Node> items = ((SequenceNode) rulesNode).getValue();
        final List<RuleLines> out = new ArrayList<>(items.size());
        for (final Node item : items) {
            int expLine = 0;
            if (item instanceof MappingNode) {
                for (final NodeTuple tuple : ((MappingNode) item).getValue()) {
                    if ("exp".equals(scalarKey(tuple.getKeyNode()))) {
                        expLine = lineOf(tuple.getKeyNode());
                        break;
                    }
                }
            }
            out.add(new RuleLines(lineOf(item), expLine));
        }
        return out;
    }

    private static String scalarKey(final Node node) {
        return node instanceof ScalarNode ? ((ScalarNode) node).getValue() : null;
    }

    /** SnakeYAML marks are 0-based; every consumer of this class speaks 1-based lines. */
    private static int lineOf(final Node node) {
        return node == null || node.getStartMark() == null ? 0 : node.getStartMark().getLine() + 1;
    }
}
