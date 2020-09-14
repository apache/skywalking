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

package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.agent.core.logging.core.converters.LiteralConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser of LogPattern. It is used to parse a pattern to the List of Converter.
 */
public class Parser {
    private final Map<String, Class<? extends Converter>> convertMaps;

    enum State {
        LITERAL_STATE, KEYWORD_STATE
    }

    public static final char ESCAPE_CHAR = '\\';
    public static final char PERCENT_CHAR = '%';

    private final String pattern;
    private final int patternLength;
    private int pointer = 0;
    private State state = State.LITERAL_STATE;

    public Parser(String pattern, Map<String, Class<? extends Converter>> convertMaps) {
        if (pattern == null || pattern.length() == 0) {
            throw new IllegalArgumentException("null or empty pattern string not allowed");
        }
        this.convertMaps = convertMaps;
        this.pattern = pattern;
        this.patternLength = pattern.length();
    }

    public List<Converter> parse() {
        List<Converter> patternConverters = new ArrayList<Converter>();
        StringBuilder buf = new StringBuilder();
        while (pointer < patternLength) {
            char c = pattern.charAt(pointer);
            pointer++;
            switch (state) {
                case LITERAL_STATE:
                    handleLiteralState(c, buf, patternConverters);
                    break;
                case KEYWORD_STATE:
                    handleKeywordState(c, buf, patternConverters);
                    break;
                default:
            }
        }

        switch (state) {
            case LITERAL_STATE:
                addConverter(buf, patternConverters, LiteralConverter.class);
                break;
            case KEYWORD_STATE:
                addConverterWithKeyword(buf, patternConverters);
                break;
            default:
        }
        return combineLiteral(patternConverters);
    }

    private List<Converter> combineLiteral(List<Converter> patternConverters) {
        List<Converter> converterList = new ArrayList<Converter>();
        StringBuilder stringBuilder = new StringBuilder();
        for (Converter patternConverter : patternConverters) {
            if (patternConverter instanceof LiteralConverter) {
                stringBuilder.append(patternConverter.convert(null));
            } else {
                if (stringBuilder.length() > 0) {
                    converterList.add(new LiteralConverter(stringBuilder.toString()));
                    stringBuilder.setLength(0);
                }
                converterList.add(patternConverter);
            }
        }
        return converterList;
    }

    private void handleKeywordState(char c, StringBuilder buf, List<Converter> patternConverters) {
        if (Character.isJavaIdentifierPart(c)) {
            buf.append(c);
        } else if (c == PERCENT_CHAR) {
            addConverterWithKeyword(buf, patternConverters);
        } else {
            addConverterWithKeyword(buf, patternConverters);
            if (c == ESCAPE_CHAR) {
                escape("%", buf);
            } else {
                buf.append(c);
            }
            state = State.LITERAL_STATE;
        }
    }

    private void addConverterWithKeyword(StringBuilder buf, List<Converter> patternConverters) {
        String keyword = buf.toString();
        if (convertMaps.containsKey(keyword)) {
            addConverter(buf, patternConverters, convertMaps.get(keyword));
        } else {
            buf.insert(0, "%");
            addConverter(buf, patternConverters, LiteralConverter.class);
        }
    }

    private void handleLiteralState(char c, StringBuilder buf, List<Converter> patternConverters) {
        switch (c) {
            case ESCAPE_CHAR:
                escape("%", buf);
                break;
            case PERCENT_CHAR:
                addConverter(buf, patternConverters, LiteralConverter.class);
                state = State.KEYWORD_STATE;
                break;
            default:
                buf.append(c);
        }

    }

    private void escape(String escapeChars, StringBuilder buf) {
        if (pointer < patternLength) {
            char next = pattern.charAt(pointer++);
            escape(escapeChars, buf, next);
        }
    }

    private void addConverter(StringBuilder buf, List<Converter> patternConverters, Class<? extends Converter> aClass) {
        if (buf.length() > 0) {
            String result = buf.toString();
            if (LiteralConverter.class.equals(aClass)) {
                patternConverters.add(new LiteralConverter(result));
            } else {
                try {
                    patternConverters.add(aClass.newInstance());
                } catch (Exception e) {
                    throw new IllegalStateException("Create Converter error. Class: " + aClass, e);
                }
            }
            buf.setLength(0);
        }
    }

    private void escape(String escapeChars, StringBuilder buf, char next) {
        if (escapeChars.indexOf(next) >= 0) {
            buf.append(next);
        } else {
            switch (next) {
                case '_':
                    // the \_ sequence is swallowed
                    break;
                case '\\':
                    buf.append(next);
                    break;
                case 't':
                    buf.append('\t');
                    break;
                case 'r':
                    buf.append('\r');
                    break;
                case 'n':
                    buf.append('\n');
                    break;
                default:
                    throw new IllegalArgumentException("Illegal char " + next + ". It not allowed as escape characters.");
            }
        }
    }

}
