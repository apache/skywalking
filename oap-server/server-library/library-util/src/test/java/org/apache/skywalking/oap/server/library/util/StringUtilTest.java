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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilTest {
    @Test
    public void testIsEmpty() {
        assertTrue(StringUtil.isEmpty(null));
        assertTrue(StringUtil.isEmpty(""));
        assertFalse(StringUtil.isEmpty("   "));
        assertFalse(StringUtil.isEmpty("A String"));
    }

    @Test
    public void testIsBlank() {
        assertTrue(StringUtil.isBlank(null));
        assertTrue(StringUtil.isBlank(""));
        assertTrue(StringUtil.isBlank("   "));
        assertFalse(StringUtil.isBlank("A String"));
    }

    @Test
    public void testJoin() {
        assertNull(StringUtil.join('.'));
        assertEquals("Single part.", StringUtil.join('.', "Single part."));
        assertEquals("part1.part2.p3", StringUtil.join('.', "part1", "part2", "p3"));
        assertEquals("E", StringUtil.join('E', new String[2]));
    }

    @Test
    public void testSubstringMatchReturningTrue() {
        StringBuffer stringBuffer = new StringBuffer("ZP~>xz1;");
        assertTrue(StringUtil.substringMatch(stringBuffer, 0, stringBuffer));
    }

    @Test
    public void testSubstringMatchWithPositive() {
        assertFalse(StringUtil.substringMatch("", 4770, ""));
    }

    @Test
    public void testCut() {
        String str = "aaaaaaabswbswbbsbwbsbbwbsbwbsbwbbsbbebewewewewewewewewewewew";
        String shortStr = "ab";
        assertEquals(10, StringUtil.cut(str, 10).length());
        assertEquals(2, StringUtil.cut(shortStr, 10).length());
    }

    @Test
    public void testTrim() {
        assertEquals(StringUtil.trim("aaabcdefaaa", 'a'), "bcdef");
        assertEquals(StringUtil.trim("bcdef", 'a'), "bcdef");
        assertEquals(StringUtil.trim("abcdef", 'a'), "bcdef");
        assertEquals(StringUtil.trim("abcdef", 'f'), "abcde");
    }

    @Test
    public void testTrimJson() {
        String jsonString = "{\"k1\":\"v1\",\"k2\":\"v2\",\"k3\":true,\"k4\":[{\"a\":\"b\",\"c\":\"d\"},{\"e\":\"f\",\"g\":\"h\"}]}";
        assertEquals(StringUtil.trimJson(jsonString, 10), "{}");
        assertTrue(StringUtil.trimJson(jsonString, 10).length() <= 10);
        assertEquals(StringUtil.trimJson(jsonString, 11), "{\"k1\":\"v1\",\"k2\":\"v2\"}");
        assertTrue(StringUtil.trimJson(jsonString, 11).length() <= 11);
        jsonString = "[{\"a\":\"b\",\"c\":\"d\"},{\"e\":\"f\",\"g\":\"h\"}]";
        assertEquals(StringUtil.trimJson(jsonString, 37), "[{\"a\":\"b\",\"c\":\"d\"},{\"e\":\"f\",\"g\":\"h\"}]");
        assertEquals(StringUtil.trimJson(jsonString, 37).length(), 37);
        assertEquals(StringUtil.trimJson(jsonString, 36), "[{\"a\":\"b\",\"c\":\"d\"}]");
        assertTrue(StringUtil.trimJson(jsonString, 36).length() <= 36);
    }

}
