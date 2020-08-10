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

package org.apache.skywalking.oap.server.library.util.prometheus.parser.sample;

enum State {
    NAME {
        @Override
        State nextState(char c, Context ctx) {
            if (c == '{') {
                return START_OF_LABEL_NAME;
            } else if (isWhitespace(c)) {
                return END_OF_NAME;
            }
            ctx.name.append(c);
            return this;
        }
    },
    END_OF_NAME {
        @Override
        State nextState(final char c, final Context ctx) {
            if (isWhitespace(c)) {
                return this;
            } else if (c == '{') {
                return START_OF_LABEL_NAME;
            }
            ctx.value.append(c);
            return VALUE;
        }
    },
    START_OF_LABEL_NAME {
        @Override
        State nextState(final char c, final Context ctx) {
            if (isWhitespace(c)) {
                return this;
            } else if (c == '}') {
                return END_OF_LABELS;
            }
            ctx.labelname.append(c);
            return LABEL_NAME;
        }
    },
    LABEL_NAME {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == '=') {
                return LABEL_VALUE_QUOTE;
            } else if (c == '}') {
                return END_OF_LABELS;
            } else if (State.isWhitespace(c)) {
                return LABEL_VALUE_EQUALS;
            }
            ctx.labelname.append(c);
            return this;
        }
    },
    LABEL_VALUE_EQUALS {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == '=') {
                return LABEL_VALUE_QUOTE;
            } else if (State.isWhitespace(c)) {
                return this;
            }
            return INVALID;
        }
    },
    LABEL_VALUE_QUOTE {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == '"') {
                return LABEL_VALUE;
            } else if (State.isWhitespace(c)) {
                return this;
            }
            return INVALID;
        }
    },
    LABEL_VALUE {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == '\\') {
                return LABEL_VALUE_SLASH;
            } else if (c == '"') {
                ctx.labels.put(ctx.labelname.toString(), ctx.labelvalue.toString());
                ctx.labelname.setLength(0);
                ctx.labelvalue.setLength(0);
                return NEXT_LABEL;
            }
            ctx.labelvalue.append(c);
            return this;
        }
    },
    LABEL_VALUE_SLASH {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == '\\') {
                ctx.labelvalue.append('\\');
            } else if (c == 'n') {
                ctx.labelvalue.append('\n');
            } else if (c == '"') {
                ctx.labelvalue.append('"');
            }
            ctx.labelvalue.append('\\').append(c);
            return LABEL_VALUE;
        }
    },
    NEXT_LABEL {
        @Override
        State nextState(final char c, final Context ctx) {
            if (c == ',') {
                return LABEL_NAME;
            } else if (c == '}') {
                return END_OF_LABELS;
            } else if (State.isWhitespace(c)) {
                return this;
            }
            return INVALID;
        }
    },
    END_OF_LABELS {
        @Override
        State nextState(final char c, final Context ctx) {
            if (State.isWhitespace(c)) {
                return this;
            }
            ctx.value.append(c);
            return VALUE;
        }
    },
    VALUE {
        @Override
        State nextState(final char c, final Context ctx) {
            if (State.isWhitespace(c)) {
                // TODO: timestamps
                return END;
            }
            ctx.value.append(c);
            return this;
        }
    },
    END {
        @Override
        State nextState(final char c, final Context ctx) {
            throw new IllegalStateException();
        }
    },
    INVALID {
        @Override
        State nextState(final char c, final Context ctx) {
            throw new IllegalStateException();
        }
    };

    abstract State nextState(char c, Context ctx);

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }
}
