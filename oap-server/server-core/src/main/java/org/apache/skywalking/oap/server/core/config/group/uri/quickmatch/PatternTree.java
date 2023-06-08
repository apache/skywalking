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
        final String[] tokens = pattern.split("/");

        PatternToken current = null;
        for (final PatternToken patternToken : roots) {
            if (patternToken.isMatch(tokens[0])) {
                current = patternToken;
                break;
            }
        }

        if (current == null) {
            current = new StringToken(tokens[0]);
            roots.add(current);
        }

        if (tokens.length == 1) {
            current.setExpression(pattern);
            return;
        }

        for (int i = 1; i < tokens.length; i++) {
            final String token = tokens[i];
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

    public StringFormatGroup.FormatResult match(String uri) {
        final String[] slices = uri.split("/");
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
                return new StringFormatGroup.FormatResult(false, uri, null);
            }
            current = matchedToken.children();
        }
        if (matchedToken.isLeaf()) {
            return new StringFormatGroup.FormatResult(true, uri, matchedToken.expression());
        } else {
            return new StringFormatGroup.FormatResult(false, uri, null);
        }
    }
}
