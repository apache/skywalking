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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link DslGeneratedFileWriter#lineOfMethod} decides which line a generated method's single
 * {@code LineNumberTable} entry points at. A wrong answer is not caught by anything downstream:
 * the attribute is stamped unconditionally, so the wrong line ships in production bytecode and
 * only shows up as a stack frame naming the wrong rule.
 *
 * <p>The name pair here is not hypothetical. OAL declares {@code serialize} and {@code deserialize}
 * on every generated metrics class, and {@code "deserialize("} contains {@code "serialize("}.
 */
class DslGeneratedFileWriterLineOfMethodTest {

    /** The OAL metrics-class shape, with deserialize emitted FIRST — the ordering nothing pins. */
    private static final String DESERIALIZE_FIRST =
        "package rt;\n"                                                                  // 1
            + "\n"                                                                       // 2
            + "public class Probe {\n"                                                   // 3
            + "\n"                                                                       // 4
            + "    public void deserialize(org.apache.skywalking.RemoteData data) {\n"   // 5
            + "        this.value = data.getValue();\n"                                  // 6
            + "    }\n"                                                                  // 7
            + "\n"                                                                       // 8
            + "    public org.apache.skywalking.RemoteData.Builder serialize() {\n"      // 9
            + "        return builder;\n"                                                // 10
            + "    }\n"                                                                  // 11
            + "}\n";                                                                     // 12

    private static int line(final String source, final String method) {
        return DslGeneratedFileWriter.lineOfMethod(source, method);
    }

    /** Lines are reported in the WRITTEN file, which the writer prefixes with a licence header. */
    private static int inFile(final int lineInWrapper) {
        return DslGeneratedFileWriter.SOURCE_FILE_PREAMBLE_LINES + lineInWrapper;
    }

    @Test
    void aShorterNameIsNotMatchedByALongerMethodEndingWithIt() {
        // Before the boundary check this returned deserialize's line, because the scan is
        // top-down and "deserialize(" contains "serialize(".
        assertEquals(inFile(9), line(DESERIALIZE_FIRST, "serialize"));
        assertEquals(inFile(5), line(DESERIALIZE_FIRST, "deserialize"));
        assertNotEquals(line(DESERIALIZE_FIRST, "serialize"), line(DESERIALIZE_FIRST, "deserialize"));
    }

    @Test
    void theAnswerDoesNotDependOnDeclarationOrder() {
        final String serializeFirst =
            "package rt;\n"                                                                  // 1
                + "\n"                                                                       // 2
                + "public class Probe {\n"                                                   // 3
                + "\n"                                                                       // 4
                + "    public org.apache.skywalking.RemoteData.Builder serialize() {\n"      // 5
                + "        return builder;\n"                                                // 6
                + "    }\n"                                                                  // 7
                + "\n"                                                                       // 8
                + "    public void deserialize(org.apache.skywalking.RemoteData data) {\n"   // 9
                + "        this.value = data.getValue();\n"                                  // 10
                + "    }\n"                                                                  // 11
                + "}\n";                                                                     // 12

        // Each method lands on its own declaration whichever is emitted first. Reordering
        // METRICS_CLASS_METHODS is a cosmetic-looking edit that used to change attribution.
        assertEquals(inFile(5), line(serializeFirst, "serialize"));
        assertEquals(inFile(9), line(serializeFirst, "deserialize"));
    }

    @Test
    void anUnderscorePrefixedOrCamelCasedNeighbourDoesNotMatchEither() {
        final String source =
            "package rt;\n"                                                       // 1
                + "\n"                                                            // 2
                + "public class Probe {\n"                                        // 3
                + "\n"                                                            // 4
                + "    private void doCommandoCpm(Service source) {\n"            // 5
                + "    }\n"                                                       // 6
                + "\n"                                                            // 7
                + "    private void doCpm(Service source) {\n"                    // 8
                + "    }\n"                                                       // 9
                + "\n"                                                            // 10
                + "    private StorageID _getid0() {\n"                           // 11
                + "    }\n"                                                       // 12
                + "\n"                                                            // 13
                + "    protected StorageID id0() {\n"                             // 14
                + "    }\n"                                                       // 15
                + "}\n";                                                          // 16

        // doCpm is reachable from operator-authored OAL: two metrics named cpm and commando_cpm
        // in one scope generate exactly this pair on the shared dispatcher.
        assertEquals(inFile(8), line(source, "doCpm"));
        assertEquals(inFile(5), line(source, "doCommandoCpm"));
        // '_' is an identifier character, so _getid0 must not answer for id0.
        assertEquals(inFile(14), line(source, "id0"));
    }

    @Test
    void aNameThatAppearsOnlyInsideEmbeddedRuleTextIsNotADeclaration() {
        final String source =
            "package rt;\n"                                                                 // 1
                + "\n"                                                                      // 2
                + "public class Probe {\n"                                                  // 3
                + "\n"                                                                      // 4
                + "    public static final String DSL = \"filter { run(x) }\";\n"           // 5
                + "\n"                                                                      // 6
                + "    public SampleFamily run(java.util.Map samples) {\n"                  // 7
                + "        return result;\n"                                                // 8
                + "    }\n"                                                                 // 9
                + "}\n";                                                                    // 10

        // The verbatim DSL is spliced in as a string literal ahead of the method. It is a
        // declaration-shaped line only if the modifier and suffix checks are ignored.
        assertEquals(inFile(7), line(source, "run"));
    }

    @Test
    void anAbsentMethodStaysUnresolvedRatherThanGuessing() {
        assertEquals(DslSourceRef.UNRESOLVED, line(DESERIALIZE_FIRST, "toHour"));
        assertEquals(DslSourceRef.UNRESOLVED, line(null, "run"));
        assertEquals(DslSourceRef.UNRESOLVED, line(DESERIALIZE_FIRST, null));
    }
}
