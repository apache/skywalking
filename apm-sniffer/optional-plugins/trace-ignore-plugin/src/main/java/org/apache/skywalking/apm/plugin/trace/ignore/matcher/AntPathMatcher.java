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

package org.apache.skywalking.apm.plugin.trace.ignore.matcher;

import org.apache.skywalking.apm.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author liujc [liujunc1993@163.com]
 *
 */
public class AntPathMatcher implements TracePathMatcher {

    private static final String DEFAULT_PATH_SEPARATOR = "/";

    private static final String ANY_ONE_MATCHING_CHAR = "?";

    private static final String ANY_MATCHING_CHAR = "*";

    private static final String MULTILEVEL_DIRECTORIES_ANY_MATCHING_CHAR = ANY_MATCHING_CHAR.concat(ANY_MATCHING_CHAR);

    @Override
    public boolean match(String pattern, String path) {
        if (!MatchAssist.checkPatternAndPath(pattern, path)) {
            return false;
        }

        // resolve pattern and path by default path separator
        String[] resolvedPatterns = MatchAssist.resolvePath(pattern);
        String[] resolvedPaths = MatchAssist.resolvePath(path);

        int patternIdxStart = 0;
        int patternIdxEnd = resolvedPatterns.length - 1;
        int pathIdxStart = 0;
        int pathIdxEnd = resolvedPaths.length - 1;


        // try to match first '**'
        while (true) {
            if (patternIdxStart > patternIdxEnd || pathIdxStart > pathIdxEnd) {
                break;
            }
            String resolvedPattern = resolvedPatterns[patternIdxStart];
            if (MULTILEVEL_DIRECTORIES_ANY_MATCHING_CHAR.equals(resolvedPattern)) {
                break;
            }
            if (!MatchAssist.matchStrings(resolvedPattern, resolvedPaths[pathIdxStart])) {
                return false;
            }
            patternIdxStart++;
            pathIdxStart++;
        }

        if (pathIdxStart > pathIdxEnd) {
            if (patternIdxStart > patternIdxEnd) {
                return pattern.endsWith(DEFAULT_PATH_SEPARATOR) == path.endsWith(DEFAULT_PATH_SEPARATOR);
            }
            return patternIdxStart == patternIdxEnd && resolvedPatterns[patternIdxStart].equals(ANY_MATCHING_CHAR) && path.endsWith(DEFAULT_PATH_SEPARATOR) 
                    || MatchAssist.checkPatternIdx(patternIdxStart, patternIdxEnd, resolvedPatterns);
        }
        else if (patternIdxStart > patternIdxEnd) {
            return false;
        }

        // try to match last '**'
        while (true) {
            if (patternIdxStart > patternIdxEnd || pathIdxStart > pathIdxEnd) {
                break;
            }
            String resolvedPattern = resolvedPatterns[patternIdxEnd];
            if (resolvedPattern.equals(MULTILEVEL_DIRECTORIES_ANY_MATCHING_CHAR)) {
                break;
            }
            if (!MatchAssist.matchStrings(resolvedPattern, resolvedPaths[pathIdxEnd])) {
                return false;
            }
            patternIdxEnd--;
            pathIdxEnd--;
        }
        if (pathIdxStart > pathIdxEnd) {
            return MatchAssist.checkPatternIdx(patternIdxStart, patternIdxEnd, resolvedPatterns);
        }

        while (patternIdxStart != patternIdxEnd && pathIdxStart <= pathIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patternIdxStart + 1; i <= patternIdxEnd; i++) {
                if (resolvedPatterns[i].equals(MULTILEVEL_DIRECTORIES_ANY_MATCHING_CHAR)) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patternIdxStart + 1) {
                // '**/**' situation, so skip one
                patternIdxStart++;
                continue;
            }
            // Find the pattern between patternIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = patIdxTmp - patternIdxStart - 1;
            int strLength = pathIdxEnd - pathIdxStart + 1;
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    String subPat = resolvedPatterns[patternIdxStart + j + 1];
                    String subStr = resolvedPatterns[pathIdxStart + i + j];
                    if (!MatchAssist.matchStrings(subPat, subStr)) {
                        continue strLoop;
                    }
                }
                foundIdx = pathIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patternIdxStart = patIdxTmp;
            pathIdxStart = foundIdx + patLength;
        }

        return MatchAssist.checkPatternIdx(patternIdxStart, patternIdxEnd, resolvedPatterns);
    }




    private static class  MatchAssist {

        private static final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");

        private static final String DEFAULT_VARIABLE_PATTERN = "(.*)";

        private static final ConcurrentMap<String, Pattern> GLOBAL_COMPILED_PATTERN_CACHE = new ConcurrentHashMap<String, Pattern>();


        private static boolean checkPatternIdx(int patternIdxStart, int patternIdxEnd, String[] resolvedPatterns) {
            for (int i = patternIdxStart; i <= patternIdxEnd; i++) {
                if (!resolvedPatterns[i].equals(MULTILEVEL_DIRECTORIES_ANY_MATCHING_CHAR)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * make sure of the pattern and path is validate
         */
        private static boolean checkPatternAndPath(String pattern, String path) {
            return !StringUtil.isEmpty(pattern) && !StringUtil.isEmpty(path) &&
                    path.startsWith(DEFAULT_PATH_SEPARATOR) == pattern.startsWith(DEFAULT_PATH_SEPARATOR);
        }

        /**
         * resolve path by default path separator
         */
        private static String[] resolvePath(String path) {
            if (path == null) {
                return null;
            }
            StringTokenizer st = new StringTokenizer(path, DEFAULT_PATH_SEPARATOR);
            List<String> tokens = new ArrayList<String>();
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                token = token.trim();
                if (token.length() > 0) {
                    tokens.add(token);
                }
            }
            return tokens.toArray(new String[tokens.size()]);
        }

        /**
         *  use pattern match path
         */
        private static boolean matchStrings(String pattern, String path) {
            if (StringUtil.isEmpty(pattern) || StringUtil.isEmpty(path)) {
                return false;
            }
            // if this pattern has been compiled
            Pattern compliedPattern = GLOBAL_COMPILED_PATTERN_CACHE.get(pattern);
            if (compliedPattern == null) {
                // build new pattern
                StringBuilder patternBuilder = new StringBuilder();
                Matcher matcher = GLOB_PATTERN.matcher(pattern);
                int end = 0;
                while (matcher.find()) {
                    patternBuilder.append(quote(pattern, end, matcher.start()));
                    String match = matcher.group();
                    if (ANY_ONE_MATCHING_CHAR.equals(match)) {
                        patternBuilder.append('.');
                    }
                    else if (ANY_MATCHING_CHAR.equals(match)) {
                        patternBuilder.append(".".concat(ANY_MATCHING_CHAR));
                    }
                    else if (match.startsWith("{") && match.endsWith("}")) {
                        int colonIdx = match.indexOf(':');
                        if (colonIdx == -1) {
                            patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
                        }
                        else {
                            String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
                            patternBuilder.append('(');
                            patternBuilder.append(variablePattern);
                            patternBuilder.append(')');
                        }
                    }
                    end = matcher.end();
                }
                patternBuilder.append(quote(pattern, end, pattern.length()));
                compliedPattern = Pattern.compile(patternBuilder.toString());
                GLOBAL_COMPILED_PATTERN_CACHE.putIfAbsent(pattern, compliedPattern);
            }

            return compliedPattern.matcher(path).matches();
        }

        private static String quote(String s, int start, int end) {
            if (start == end) {
                return "";
            }
            return Pattern.quote(s.substring(start, end));
        }
    }
}
