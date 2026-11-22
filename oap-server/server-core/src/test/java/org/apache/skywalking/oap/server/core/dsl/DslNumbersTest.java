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

package org.apache.skywalking.oap.server.core.dsl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DslNumbersTest {

    /**
     * A number of a type no engine knows, as Gson's lazily parsed JSON number.
     */
    private static final class TextNumber extends Number {
        private final String text;

        TextNumber(final String text) {
            this.text = text;
        }

        @Override
        public int intValue() {
            return (int) doubleValue();
        }

        @Override
        public long longValue() {
            return (long) doubleValue();
        }

        @Override
        public float floatValue() {
            return (float) doubleValue();
        }

        @Override
        public double doubleValue() {
            return Double.parseDouble(text);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @Test
    void toBooleanTakesABooleanTheTextTrueOrFalseInAnyCaseAndANumberByZero() {
        for (final Object yes : new Object[] {true, "true", "TRUE", "True", " true ", "1", " 2 ", "-1", "1e3", 1, 2L,
            -1, 0.5, Double.POSITIVE_INFINITY, new BigDecimal("1e-400"), BigInteger.TEN}) {
            assertTrue(DslNumbers.toBoolean(yes), String.valueOf(yes));
        }
        for (final Object no : new Object[] {false, "false", "FALSE", "False", "0", " 0.0 ", "-0", 0, 0L, 0.0, -0.0,
            BigDecimal.ZERO, BigInteger.ZERO}) {
            assertFalse(DslNumbers.toBoolean(no), String.valueOf(no));
        }
        for (final Object neither : new Object[] {"yes", "abc", "NaN", "", Double.NaN, Float.NaN, 'a', List.of(true)}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DslNumbers.toBoolean(neither), String.valueOf(neither));
            assertTrue(e.getMessage().contains("cannot be converted to boolean"), e.getMessage());
        }
    }

    @Test
    public void aNumberOfAnyTypeKeepsItsDecimalValue() {
        final Number amount = new TextNumber("1.9");
        assertTrue(DslNumbers.compare(amount, 1) > 0);
        assertTrue(DslNumbers.compare(1, amount) < 0);
        assertEquals(new BigDecimal("2.9"), DslNumbers.add(amount, 1));
        // a number of another type that is NaN or infinite computes in double, as it has no exact value
        assertTrue(Double.isNaN(DslNumbers.add(new TextNumber("NaN"), 1).doubleValue()));
        assertTrue(Double.isInfinite(DslNumbers.arithmetic(new TextNumber("Infinity"), 2, '*').doubleValue()));
        // a NaN has no order; -0.0 equals 0.0, as Java compares them
        assertTrue(DslNumbers.isUnordered(Double.NaN, 1));
        assertEquals(0, DslNumbers.compare(-0.0d, 0.0d));
        assertEquals(3L, DslNumbers.add(1, 2L));
        assertEquals(0, DslNumbers.compare(3, 3L));
    }

    @Test
    public void aBooleanJoinsOnlyATextOnItsLeft() {
        assertEquals("xtrue", DslNumbers.plus("x", true));
        for (final Object[] pair : new Object[][] {{true, "-x"}, {1, true}, {true, true}, {null, true}}) {
            assertThrows(IllegalArgumentException.class, () -> DslNumbers.plus(pair[0], pair[1]),
                pair[0] + " + " + pair[1]);
        }
        assertEquals("bb", DslNumbers.plus('b', 'b'), "two characters join, as LAL and TAL always did");
        assertEquals("b1", DslNumbers.plus('b', 1));
        assertEquals("b1", DslNumbers.plus("b", 1));
    }

    @Test
    public void conversionsReadTextsCharactersAndBlanks() {
        assertEquals(7, DslNumbers.toInt(new StringBuilder(" 7 ")));
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.toLong('5'), "a character is no number");
        assertTrue(DslNumbers.isBlank(" "));
        assertTrue(DslNumbers.isBlank(null));
        assertFalse(DslNumbers.isBlank("0"));
        assertNotNull(DslNumbers.numberIn(" 3 "));
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.toInt(List.of()));
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.toDouble(Map.of()));
    }

    @Test
    public void aTextJoinsOnlyOntoATextANumberACharacterOrNull() {
        assertEquals("5x", DslNumbers.plus(5, "x"));
        assertEquals("cx", DslNumbers.plus('c', "x"));
        assertEquals("nullx", DslNumbers.plus(null, "x"));
        assertEquals("xtrue", DslNumbers.plus("x", true));
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.plus(List.of(1), "x"));
        final IllegalArgumentException right = assertThrows(IllegalArgumentException.class,
            () -> DslNumbers.plus("x", new String[] {"a"}), "an array is no text on either side");
        assertTrue(right.getMessage().contains("takes no part in a text +"), right.getMessage());
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.joined(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> DslNumbers.plus(Thread.State.NEW, "x"));
        assertTrue(DslNumbers.mayJoinText(Object.class), "decided when the rule runs");
        assertTrue(DslNumbers.mayJoinText(int.class));
        assertFalse(DslNumbers.mayJoinText(String[].class));
        assertFalse(DslNumbers.mayJoinText(Map.class));
        assertFalse(DslNumbers.mayJoinText(Thread.State.class));
    }

    @Test
    public void aQuotientIsGroovysDecimal() {
        assertEquals(new BigDecimal("3.5"), DslNumbers.quotient(BigDecimal.valueOf(7), BigDecimal.valueOf(2)));
        assertEquals(new BigDecimal("0.3333333333"), DslNumbers.quotient(BigDecimal.ONE, BigDecimal.valueOf(3)));
        assertThrows(ArithmeticException.class, () -> DslNumbers.quotient(BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    public void aNanHasNoOrder() {
        assertEquals(Integer.valueOf(-1), DslNumbers.order(1, 2L));
        assertEquals(Integer.valueOf(0), DslNumbers.order(-0.0d, 0));
        assertEquals(null, DslNumbers.order(Double.NaN, 1));
    }

    @Test
    public void aLeadingZeroMakesAnIntegerLiteralOctal() {
        assertEquals(8, DslNumbers.wholeLiteral("010", "010").intValue());
        assertEquals(10, DslNumbers.wholeLiteral("10", "10").intValue());
        assertEquals(0, DslNumbers.wholeLiteral("0", "0").intValue());
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> DslNumbers.wholeLiteral("08", "The literal 08"));
        assertTrue(e.getMessage().startsWith("The literal 08 is no octal number"), e.getMessage());
    }
}
