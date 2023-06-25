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

package org.apache.skywalking.oap.server.library.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

public final class StringUtil {
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isBlank(String str) {
        return str == null || isEmpty(str.trim());
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String join(final char delimiter, final String... strings) {
        if (strings.length == 0) {
            return null;
        }
        if (strings.length == 1) {
            return strings[0];
        }
        int length = strings.length - 1;
        for (final String s : strings) {
            if (s == null) {
                continue;
            }
            length += s.length();
        }
        final StringBuilder sb = new StringBuilder(length);
        if (strings[0] != null) {
            sb.append(strings[0]);
        }
        for (int i = 1; i < strings.length; ++i) {
            if (!isEmpty(strings[i])) {
                sb.append(delimiter).append(strings[i]);
            } else {
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
        if (index + substring.length() > str.length()) {
            return false;
        }
        for (int i = 0; i < substring.length(); i++) {
            if (str.charAt(index + i) != substring.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static String cut(String str, int threshold) {
        if (isEmpty(str) || str.length() <= threshold) {
            return str;
        }
        return str.substring(0, threshold);
    }

    public static String trim(final String str, final char ch) {
        if (isEmpty(str)) {
            return null;
        }

        final char[] chars = str.toCharArray();

        int i = 0, j = chars.length - 1;
        // noinspection StatementWithEmptyBody
        for (; i < chars.length && chars[i] == ch; i++) {
        }
        // noinspection StatementWithEmptyBody
        for (; j > 0 && chars[j] == ch; j--) {
        }

        return new String(chars, i, j - i + 1);
    }

    /**
     * trim json string
     *
     * @param jsonString original json string
     * @param maxLength limit length
     * @return trimmed json string
     */
    public static String trimJson(String jsonString, int maxLength) {
        JsonElement jsonElement = JsonParser.parseString(jsonString);
        return trimJsonElement(jsonElement, maxLength).toString();
    }

    private static JsonElement trimJsonElement(JsonElement jsonElement, int maxLength) {
        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonObject trimmedJsonObject = new JsonObject();
            int currentLength = 0;
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                JsonElement trimmedValue = trimJsonElement(value, maxLength - currentLength);
                int valueLength = trimmedValue.toString().length();
                if (currentLength + valueLength > maxLength) {
                    break;
                }
                trimmedJsonObject.add(key, trimmedValue);
                currentLength += valueLength;
            }
            return trimmedJsonObject;
        } else if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            JsonArray trimmedJsonArray = new JsonArray();
            int currentLength = 0;
            for (JsonElement element : jsonArray) {
                JsonElement trimmedElement = trimJsonElement(element, maxLength - currentLength);
                int elementLength = trimmedElement.toString().length();
                if (currentLength + elementLength > maxLength) {
                    break;
                }
                trimmedJsonArray.add(trimmedElement);
                currentLength += elementLength;
            }
            return trimmedJsonArray;
        } else {
            return jsonElement;
        }
    }
}
