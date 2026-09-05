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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Finds the raw text of each part's <code>data</code> value in one record line, so a part's data is rendered
 * as the Sessionizer wrote it. Go keeps that value as <code>json.RawMessage</code> and prints it verbatim, escapes,
 * key order and spacing included; a value that was parsed and printed again would differ in all three and break
 * the document's equality with the Sessionizer's, and clip at another byte.
 *
 * <p>This is a walk over the line's JSON syntax, not a parser: it only needs the start and the end of each
 * value, and it leaves the decoding to Gson, which has already accepted the line.
 */
final class RawJson {
    private final String text;
    private int pos;

    private RawJson(final String text) {
        this.text = text;
    }

    /**
     * @param line one record line, a JSON object
     * @return the raw <code>data</code> text of each element of the line's <code>parts</code> array, in order,
     * a literal null included, null for a part without one; empty when the line has no parts or is not the JSON
     * expected
     */
    static List<String> partData(final String line) {
        try {
            return new RawJson(line).parts();
        } catch (final IllegalStateException | StringIndexOutOfBoundsException e) {
            return new ArrayList<>();
        }
    }

    private List<String> parts() {
        final List<String> out = new ArrayList<>();
        skipSpace();
        expect('{');
        while (true) {
            skipSpace();
            if (peek() == '}') {
                return out;
            }
            final String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            if ("parts".equals(key) && peek() == '[') {
                pos++;
                while (true) {
                    skipSpace();
                    if (peek() == ']') {
                        pos++;
                        break;
                    }
                    out.add(partDataOf());
                    skipSpace();
                    if (peek() == ',') {
                        pos++;
                    }
                }
            } else {
                skipValue();
            }
            skipSpace();
            if (peek() == ',') {
                pos++;
            }
        }
    }

    /**
     * @return the raw <code>data</code> of the part object at the cursor, or null; the cursor ends after the object
     */
    @Nullable
    private String partDataOf() {
        String data = null;
        expect('{');
        while (true) {
            skipSpace();
            if (peek() == '}') {
                pos++;
                return data;
            }
            final String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            final int start = pos;
            skipValue();
            if ("data".equals(key)) {
                data = text.substring(start, pos);
            }
            skipSpace();
            if (peek() == ',') {
                pos++;
            }
        }
    }

    private void skipValue() {
        final char c = peek();
        if (c == '"') {
            string();
        } else if (c == '{' || c == '[') {
            final char close = c == '{' ? '}' : ']';
            pos++;
            while (true) {
                skipSpace();
                if (peek() == close) {
                    pos++;
                    return;
                }
                if (c == '{') {
                    string();
                    skipSpace();
                    expect(':');
                    skipSpace();
                }
                skipValue();
                skipSpace();
                if (peek() == ',') {
                    pos++;
                }
            }
        } else {
            while (pos < text.length() && ",}] \t\r\n".indexOf(text.charAt(pos)) < 0) {
                pos++;
            }
        }
    }

    /**
     * @return the decoded text of the string at the cursor, escapes resolved only as far as a key needs them
     */
    private String string() {
        expect('"');
        final StringBuilder out = new StringBuilder();
        while (true) {
            final char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                final char e = text.charAt(pos++);
                if (e == 'u') {
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                } else {
                    out.append(e);
                }
            } else {
                out.append(c);
            }
        }
    }

    private void skipSpace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private char peek() {
        return text.charAt(pos);
    }

    private void expect(final char c) {
        if (text.charAt(pos) != c) {
            throw new IllegalStateException("expected " + c + " at " + pos);
        }
        pos++;
    }
}
