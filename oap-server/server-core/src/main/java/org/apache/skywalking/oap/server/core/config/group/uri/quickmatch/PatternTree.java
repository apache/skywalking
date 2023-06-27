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

package org.apache.skywalking.oap.server.core.config.group.uri.quickmatch;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;

@EqualsAndHashCode
@ToString
public class PatternTree {
    private final List<PatternToken> roots;

    public PatternTree() {
        roots = new ArrayList<>();
    }

    /**
     * The pattern is split by /, and each token is a node in the tree. Each node either a literal string or "{var}"
     * representing a variable.
     *
     * @param pattern of URIs
     */
    public void addPattern(String pattern) {
        final List<String> tokens = splitByCharacter(pattern);

        PatternToken current = null;
        for (final PatternToken patternToken : roots) {
            if (patternToken.isMatch(tokens.get(0))) {
                current = patternToken;
                break;
            }
        }

        if (current == null) {
            current = new StringToken(tokens.get(0));
            roots.add(current);
        }

        if (tokens.size() == 1) {
            current.setExpression(pattern);
            return;
        }

        for (int i = 1; i < tokens.size(); i++) {
            final String token = tokens.get(i);
            PatternToken newToken;
            if (VarToken.VAR_TOKEN.equals(token)) {
                newToken = new VarToken();
            } else {
                newToken = new StringToken(token);
            }
            final PatternToken found = current.find(newToken);
            if (found == null) {
                current = current.add(newToken);
            } else {
                current = found;
            }
        }
        current.setExpression(pattern);
    }

    List<String> splitByCharacter(String input) {
        List<String> parts = new ArrayList<>();
        int length = input.length();
        int start = 0;

        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == '/') {
                if (i == 0) {
                    start = i + 1;
                    continue;
                }
                parts.add(input.substring(start, i));
                start = i + 1;
            }
        }

        // Add the last part if necessary
        if (start < length) {
            parts.add(input.substring(start));
        }

        return parts;
    }

    public StringFormatGroup.FormatResult match(String uri) {
        final List<String> slices = splitByCharacter(uri);
        if (slices.size() == 1) {
            // Special case handling, since if a URI is just length one
            // itself will never be a variable, so simply return true and itself
            // trailing slashes, if ever encountered will be kept as is
            return new StringFormatGroup.FormatResult(true, uri, uri);
        }
        List<PatternToken> current = roots;
        PatternToken matchedToken = null;
        for (final String slice : slices) {
            boolean matched = false;
            for (final PatternToken patternToken : current) {
                if (patternToken.isMatch(slice)) {
                    matchedToken = patternToken;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return new StringFormatGroup.FormatResult(false, uri, uri);
            }
            current = matchedToken.children();
        }
        if (matchedToken.isLeaf()) {
            return new StringFormatGroup.FormatResult(true, uri, matchedToken.expression());
        } else {
            return new StringFormatGroup.FormatResult(false, uri, uri);
        }
    }

    @SuppressWarnings("unused")
    // Utility method to visualize the full tree for debugging purposes
    public String printTree() {
        StringBuilder sb = new StringBuilder();
        for (PatternToken root : roots) {
            sb.append(printNode(root, 0));
        }
        return sb.toString();
    }

    private String printNode(PatternToken node, int depth) {
        StringBuilder sb = new StringBuilder();

        sb.append("  ".repeat(Math.max(0, depth)));

        sb.append(node.toString()).append("\n");

        // Append expression if not null
        if (node.expression() != null) {
            sb.append("  ").append(node.expression()).append("\n");
        }

        if (node instanceof StringToken) {
            StringToken stringToken = (StringToken) node;
            for (PatternToken child : stringToken.children()) {
                sb.append(printNode(child, depth + 1));
            }
        } else if (node instanceof VarToken) {
            VarToken varToken = (VarToken) node;
            for (PatternToken child : varToken.children()) {
                sb.append(printNode(child, depth + 1));
            }
        }

        return sb.toString();
    }

}
