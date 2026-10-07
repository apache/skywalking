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
 */

package org.apache.skywalking.oap.server.ai.agent.conversation.withhold;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.BodyStore;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Files written for one shape each, where the fixtures hold none: a request in the forms other clients send, a
 * response that leans on a request's piece, a file whose records stop before its closing line, and a store out of
 * budget. Every check first proves the stored bytes hold the secret, so a check that found nothing would not pass
 * with the masking gone.
 */
public class WithholdingShapesTest {
    private static final String SECRET = "the system prompt nobody may see";
    private static final String TOOL_SECRET = "what the tool schema says";
    /** What the person asked, which stays in every shape but a completion's one prompt string. */
    private static final String PERSON = "a question from the person";
    private static final List<String> BOTH = Arrays.asList(Hide.SYSTEM_PROMPT, Hide.TOOL_SCHEMAS);

    /** One request body in a client's shape: the whole body is one literal, the way a first body of a chain lands. */
    @ParameterizedTest
    @ValueSource(strings = {
        // Anthropic: a string system
        "{\"model\":\"m\",\"system\":\"" + SECRET + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // OpenAI Responses: instructions, and functions
        "{\"model\":\"m\",\"instructions\":\"" + SECRET + "\",\"input\":\"" + PERSON + "\",\"functions\":[{\"name\":\"t\",\"parameters\":{\"d\":\"" + TOOL_SECRET + "\"}}]}",
        // a completion: the prompts carry the instructions and the question in one string
        "{\"prompts\":[\"System: " + SECRET + "\\nHuman: hi\"],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // Gemini: system_instruction and function declarations
        "{\"system_instruction\":{\"parts\":[{\"text\":\"" + SECRET + "\"}]},\"contents\":[{\"parts\":[{\"text\":\"" + PERSON + "\"}]}],\"functionDeclarations\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // LangChain's pair of a role and its content, and tools keyed by their names
        "{\"messages\":[[\"system\",\"" + SECRET + "\"],[\"human\",\"" + PERSON + "\"]],\"tools\":{\"t\":{\"description\":\"" + TOOL_SECRET + "\"}}}",
        // LangChain's serialized messages, a batch of one list, with a system message in its kwargs
        "{\"messages\":[[{\"lc\":1,\"type\":\"constructor\",\"id\":[\"langchain\",\"SystemMessage\"],\"kwargs\":{\"content\":\"" + SECRET + "\",\"type\":\"system\"}},{\"lc\":1,\"type\":\"constructor\",\"kwargs\":{\"content\":\"" + PERSON + "\",\"type\":\"human\"}}]],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // a developer message, as OpenAI names the instructions
        "{\"messages\":[{\"role\":\"developer\",\"content\":\"" + SECRET + "\"},{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tool_definitions\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // google-genai: the instructions and the tools in the client's own config
        "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"" + PERSON + "\"}]}],\"config\":{\"system_instruction\":\"" + SECRET + "\",\"tools\":[{\"function_declarations\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}]}}",
        // OpenAI Responses: a developer message among the input, not under messages
        "{\"model\":\"m\",\"input\":[{\"role\":\"developer\",\"content\":\"" + SECRET + "\"},{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tools\":[{\"type\":\"function\",\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // a system message as an object under a prompt key, and a block whose text is its content
        "{\"system_message\":{\"role\":\"system\",\"content\":\"" + SECRET + "\"},\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        "{\"system\":[{\"type\":\"text\",\"content\":\"" + SECRET + "\"}],\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
        // Bedrock: the tools under the client's toolConfig, each in a toolSpec
        "{\"system\":[{\"text\":\"" + SECRET + "\"}],\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"" + PERSON + "\"}]}],\"toolConfig\":{\"tools\":[{\"toolSpec\":{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\",\"inputSchema\":{\"json\":{}}}}]}}",
        // LangChain JS: a serialized system message says its role only in the class that ends its id
        "{\"messages\":[[{\"lc\":1,\"type\":\"constructor\",\"id\":[\"langchain_core\",\"messages\",\"SystemMessage\"],\"kwargs\":{\"content\":\"" + SECRET + "\",\"additional_kwargs\":{}}},{\"lc\":1,\"type\":\"constructor\",\"id\":[\"langchain_core\",\"messages\",\"HumanMessage\"],\"kwargs\":{\"content\":\"" + PERSON + "\"}}]],\"tools\":[{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}]}",
    })
    public void aRequestInAnyListedShapeIsMasked(final String body) {
        final byte[] stored = bodyFile(request("r1", body, Collections.emptyList()));
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(SECRET));
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(TOOL_SECRET));
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final String served = new String(Withholding.file(stored, BOTH, store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        assertFalse(served.contains(TOOL_SECRET), served);
        if (!body.contains("prompts")) {
            assertTrue(served.contains(PERSON), "the person's words stay: " + served);
        }
        final SessionDataFile after = SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8));
        final BodyStore again = new BodyStore();
        again.add(after);
        assertNotNull(Manifest.of(after.getRecords().get(0)));
        assertTrue(assertRebuilds(again, "r1").contains("[withheld: system_prompt]"));
    }

    @Test
    public void aResponseThatLeansOnARequestsPieceIsServedWhole() {
        // the request holds the system prompt as a piece; the response repeats it by that piece's digest
        final String piece = "\"" + SECRET + "\"";
        final String requestHead = "{\"system\":";
        final String requestTail = ",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final String requestBody = requestHead + piece + requestTail;
        final String responseHead = "{\"content\":[{\"type\":\"text\",\"text\":";
        final String responseTail = "}]}";
        final String responseBody = responseHead + piece + responseTail;
        final String pieceDigest = Digests.sha256Hex(piece.getBytes(StandardCharsets.UTF_8));
        final String request = record("r1", Manifest.ROLE_REQUEST, requestBody, Collections.singletonList(piece),
                                      segments(lit(requestHead), "{\"part\":0}", lit(requestTail)));
        final String response = record("r1.response", Manifest.ROLE_RESPONSE, responseBody, Collections.emptyList(),
                                       segments(lit(responseHead), "{\"piece\":\"" + pieceDigest + "\"}", lit(responseTail)));
        final byte[] stored = bodyFile(request, response);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        assertEquals(responseBody, assertRebuilds(store, "r1.response"));

        final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), store).getBytes();
        final SessionDataFile after = SessionDataFile.parse(served);
        final BodyStore again = new BodyStore();
        again.add(after);
        // the request lost the piece and says so; the response, which repeats the prompt as its own words, still
        // rebuilds, because it was written whole
        assertTrue(assertRebuilds(again, "r1").contains("[withheld: system_prompt]"));
        assertEquals(responseBody, assertRebuilds(again, "r1.response"));
        assertEquals(0, Manifest.of(after.getRecords().get(1)).getDepth());
    }

    @Test
    public void aFileThatStopsReadingEndsThereForAHidingReader() {
        // a line that is not a record, then a named record a reader must not see, under a closing line that checks
        final String bad = "{\"ord\":1,\"off\":\"bad\",\"sha\":\"0\",\"bytes\":1,\"parts\":[]}";
        final String named = "{\"ord\":2,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"n\",\"flags\":[\"injected\",\"system_prompt\"],"
            + "\"parts\":[{\"k\":\"text\",\"text\":\"" + SECRET + "\",\"state\":\"available\",\"bytes\":32}]}";
        final byte[] stored = file("transcript", bad, named);
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(SECRET));
        final SessionDataFile parsed = SessionDataFile.parse(stored);
        assertEquals(0, parsed.getRecords().size(), "a reader stops at the first line that is not a record");

        final Withholding.Served served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), null);
        assertTrue(served.isChanged());
        final String text = new String(served.getBytes(), StandardCharsets.UTF_8);
        assertFalse(text.contains(SECRET), text);
        assertFalse(text.contains("\"t\":\"end\""), "a file that did not read to its end gets no closing line");
        // without a hide the stored bytes go out, as they always did; so do they to a reader hiding only the tool
        // schemas, which no record of the file carries, since nothing in the file is kept from that reader
        assertEquals(stored, Withholding.file(stored, Hide.NONE, null).getBytes());
        assertEquals(stored, Withholding.file(stored, Collections.singletonList(Hide.TOOL_SCHEMAS), null).getBytes());
    }

    @Test
    public void aRequestPastTheBudgetIsMaskedWhole() {
        final String body = "{\"system\":\"" + SECRET + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final byte[] stored = bodyFile(request("r1", body, Collections.emptyList()));
        final BodyStore store = new BodyStore(16);
        store.add(SessionDataFile.parse(stored));
        final String served = new String(Withholding.file(stored, BOTH, store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET));
        final SessionDataFile.Record rec = SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8)).getRecords().get(0);
        for (final SessionDataFile.Part p : rec.getParts()) {
            assertEquals("omitted", p.getState());
        }
    }

    private static String lit(final String text) {
        final JsonObject seg = new JsonObject();
        seg.addProperty("lit", text);
        return seg.toString();
    }

    private static String segments(final String... segs) {
        return "[" + String.join(",", segs) + "]";
    }

    private static String assertRebuilds(final BodyStore store, final String id) {
        try {
            return new String(store.body(id), StandardCharsets.UTF_8);
        } catch (final BodyStore.BodyException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    /** A request record whose body is one literal, with no piece of its own. */
    private static String request(final String id, final String body, final List<String> pieces) {
        final JsonObject lit = new JsonObject();
        lit.addProperty("lit", body);
        return record(id, Manifest.ROLE_REQUEST, body, pieces, "[" + lit + "]");
    }

    /**
     * A provider body record: its pieces as data parts, then the manifest, whose digest and size are the body's, as
     * the Sessionizer's writer computes them.
     */
    private static String record(final String id, final String role, final String body, final List<String> pieces,
                                 final String segments) {
        final StringBuilder parts = new StringBuilder();
        for (final String piece : pieces) {
            parts.append("{\"k\":\"data\",\"data\":").append(piece).append(",\"state\":\"available\",\"bytes\":")
                 .append(piece.getBytes(StandardCharsets.UTF_8).length).append("},");
        }
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        final String manifest = "{\"schema\":\"provider_body/1\",\"role\":\"" + role + "\",\"src\":\"" + id + ".json\",\"sha256\":\""
            + Digests.sha256Hex(bytes) + "\",\"bytes\":" + bytes.length + ",\"depth\":0,\"model\":\"m\",\"segments\":" + segments + "}";
        parts.append("{\"k\":\"data\",\"data\":").append(manifest).append(",\"state\":\"available\",\"bytes\":")
             .append(manifest.getBytes(StandardCharsets.UTF_8).length).append("}");
        return "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":" + bytes.length + ",\"id\":\"" + id + "\",\"model\":\"m\",\"parts\":[" + parts + "]}";
    }

    private static byte[] bodyFile(final String... records) {
        return file(Manifest.KIND, records);
    }

    /** A Session Data file of the kind, with these record lines and a closing line that checks. */
    private static byte[] file(final String kind, final String... records) {
        final StringBuilder out = new StringBuilder(
            "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\",\"kind\":\"" + kind
                + "\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\".\",\"session\":\"s\",\"stream\":\"main\"}\n");
        for (final String r : records) {
            // every line must be JSON the reader accepts, so a shape that is not is a mistake of the test
            assertNotNull(Schema.parse(r), r);
            out.append(r).append('\n');
        }
        final String digest = Digests.sha256Hex(out.toString().getBytes(StandardCharsets.UTF_8));
        out.append("{\"t\":\"end\",\"records\":").append(records.length).append(",\"digest\":\"").append(digest).append("\"}\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unused")
    private static List<String> list(final String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /**
     * A system message whose content is a list of blocks, one of them a text long enough to be a piece of its own:
     * the piece goes with the block it held, since its bytes are the secret, and a long tool description held as a
     * piece goes with its tool.
     */
    @Test
    public void aPieceThatHeldAMaskedTextGoes() {
        final String longSecret = SECRET + " " + "and more of it ".repeat(80);
        final String piece = "\"" + longSecret + "\"";
        final String longTool = "\"" + TOOL_SECRET + " " + "in detail ".repeat(120) + "\"";
        final String head = "{\"messages\":[{\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":";
        final String middle = "}]},{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"functions\":[{\"name\":\"t\",\"description\":";
        final String tail = "}]}";
        final String body = head + piece + middle + longTool + tail;
        final String request = record("r1", Manifest.ROLE_REQUEST, body, Arrays.asList(piece, longTool),
                                      segments(lit(head), "{\"part\":0}", lit(middle), "{\"part\":1}", lit(tail)));
        final byte[] stored = bodyFile(request);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        assertEquals(body, assertRebuilds(store, "r1"));
        final String served = new String(Withholding.file(stored, BOTH, store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        assertFalse(served.contains(TOOL_SECRET), served);
        assertTrue(served.contains(PERSON));
        final SessionDataFile.Record rec = SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8)).getRecords().get(0);
        assertEquals(1, rec.getParts().size(), "both pieces went; the manifest stays");
    }

    @Test
    public void aRequestWithNoIdIsWithheldWhole() {
        final String body = "{\"system\":\"" + SECRET + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final byte[] stored = bodyFile(request("r1", body, Collections.emptyList()).replace("\"id\":\"r1\"", "\"id\":\"\""));
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final String served = new String(Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        for (final SessionDataFile.Part p : SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8)).getRecords().get(0).getParts()) {
            assertEquals("omitted", p.getState());
        }
    }

    @Test
    public void aFileNoReaderOpensServesNothingToAHidingReader() {
        final String named = "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"n\",\"flags\":[\"system_prompt\"],"
            + "\"parts\":[{\"k\":\"text\",\"text\":\"" + SECRET + "\",\"state\":\"available\",\"bytes\":32}]}";
        // a first line that is not a header at all
        final byte[] notAFile = ("not a header\n" + named + "\n").getBytes(StandardCharsets.UTF_8);
        final Withholding.Served none = Withholding.file(notAFile, Collections.singletonList(Hide.TOOL_SCHEMAS), null);
        assertTrue(none.isChanged());
        assertEquals(0, none.getBytes().length);
        // a header a reader may not open: the kind is missing, so the records are never read, and a reader hiding
        // the name the record carries is served the header alone
        final byte[] badHeader = ("{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"session\":\"s\"}\n" + named + "\n").getBytes(StandardCharsets.UTF_8);
        final String served = new String(Withholding.file(badHeader, Collections.singletonList(Hide.SYSTEM_PROMPT), null).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        assertTrue(served.startsWith("{\"h\":1"));
        // a reader hiding a name no record of the file carries is served the stored bytes, as always
        assertEquals(badHeader, Withholding.file(badHeader, Collections.singletonList(Hide.TOOL_SCHEMAS), null).getBytes());
        // without a hide, as stored
        assertEquals(notAFile, Withholding.file(notAFile, Hide.NONE, null).getBytes());
    }

    @Test
    public void aRequestThatCopiesAMaskedRequestIsWrittenWhole() {
        // B holds nothing of the names, but copies the front of A, which is masked and so served with another digest
        final String shared = "{\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]";
        final String bodyA = shared + ",\"system\":\"" + SECRET + "\"}";
        final String bodyB = shared + ",\"max_tokens\":1}";
        final String a = request("a", bodyA, Collections.emptyList());
        final String b = record("b", Manifest.ROLE_REQUEST, bodyB, Collections.emptyList(), segments(
            "{\"copy\":{\"from\":\"a\",\"sha256\":\"" + Digests.sha256Hex(bodyA.getBytes(StandardCharsets.UTF_8)) + "\",\"len\":"
                + shared.getBytes(StandardCharsets.UTF_8).length + "}}", lit(",\"max_tokens\":1}"))).replace("\"depth\":0", "\"depth\":1");
        final byte[] stored = bodyFile(a, b);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        assertEquals(bodyB, assertRebuilds(store, "b"));
        final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), store).getBytes();
        final SessionDataFile after = SessionDataFile.parse(served);
        final BodyStore again = new BodyStore();
        again.add(after);
        assertTrue(assertRebuilds(again, "a").contains("[withheld: system_prompt]"));
        assertEquals(bodyB, assertRebuilds(again, "b"), "B rebuilds from the served files alone");
        assertEquals(0, Manifest.of(after.getRecords().get(1)).getDepth());
    }

    /** A flag spelled with an escape, and a header written with spaces, are read as JSON is, not as bytes. */
    @Test
    public void theSpellingOfAFlagOrAHeaderDoesNotDecide() {
        final String escaped = "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"n\",\"flags\":[\"system\\u005fprompt\"],"
            + "\"parts\":[{\"k\":\"text\",\"text\":\"" + SECRET + "\",\"state\":\"available\",\"bytes\":32}]}";
        final byte[] transcript = file("transcript", escaped);
        assertTrue(new String(transcript, StandardCharsets.UTF_8).contains("u005f"));
        final String served = new String(Withholding.file(transcript, Collections.singletonList(Hide.SYSTEM_PROMPT), null).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);

        final String body = "{\"system\":\"" + SECRET + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final String spaced = new String(bodyFile(request("r1", body, Collections.emptyList())), StandardCharsets.UTF_8)
            .replaceFirst("\"kind\":\"provider_body\"", "\"kind\": \"provider_body\"");
        final byte[] stored = spaced.getBytes(StandardCharsets.UTF_8);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final String servedBody = new String(Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(servedBody.contains(SECRET), servedBody);
    }

    /** A body that cannot be rebuilt within the budget is withheld whole, a response that leans on one too. */
    @Test
    public void aBodyThatCannotBeWrittenWholeIsWithheldWhole() {
        final String piece = "\"" + SECRET + "\"";
        final String requestHead = "{\"system\":";
        final String requestTail = ",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final String responseHead = "{\"content\":[{\"type\":\"text\",\"text\":";
        final String responseTail = "}]}";
        final String pieceDigest = Digests.sha256Hex(piece.getBytes(StandardCharsets.UTF_8));
        final byte[] stored = bodyFile(
            record("r1", Manifest.ROLE_REQUEST, requestHead + piece + requestTail, Collections.singletonList(piece),
                   segments(lit(requestHead), "{\"part\":0}", lit(requestTail))),
            record("r1.response", Manifest.ROLE_RESPONSE, responseHead + piece + responseTail, Collections.emptyList(),
                   segments(lit(responseHead), "{\"piece\":\"" + pieceDigest + "\"}", lit(responseTail))));
        final BodyStore tight = new BodyStore(8);
        tight.add(SessionDataFile.parse(stored));
        final String served = new String(Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), tight).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        for (final SessionDataFile.Record rec : SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8)).getRecords()) {
            for (final SessionDataFile.Part p : rec.getParts()) {
                assertEquals("omitted", p.getState(), rec.getId());
            }
        }
    }

    /**
     * A response that leans on another response, which leans on a masked request and cannot be written whole within
     * the budget: the first is withheld whole, so the second, whose piece it held, is written whole too rather than
     * served with a reference to a piece that is gone.
     */
    @Test
    public void aResponseLeaningOnAnOmittedResponseIsWrittenWhole() {
        final String bodyA = "{\"system\":\"" + SECRET + "\"}";
        final String ok = "\"OK\"";
        final String bodyR = "{\"reply\":" + ok + "}";
        final String bodyS = "{\"again\":" + ok + "}";
        final String a = request("a", bodyA, Collections.emptyList());
        // R copies A's first byte and holds "OK" as its own piece; S uses that piece by its digest
        final String r = record("r", Manifest.ROLE_RESPONSE, bodyR, Collections.singletonList(ok), segments(
            "{\"copy\":{\"from\":\"a\",\"sha256\":\"" + Digests.sha256Hex(bodyA.getBytes(StandardCharsets.UTF_8)) + "\",\"len\":1}}",
            lit("\"reply\":"), "{\"part\":0}", lit("}"))).replace("\"depth\":0", "\"depth\":1");
        final String s = record("s", Manifest.ROLE_RESPONSE, bodyS, Collections.emptyList(), segments(
            lit("{\"again\":"), "{\"piece\":\"" + Digests.sha256Hex(ok.getBytes(StandardCharsets.UTF_8)) + "\"}", lit("}")));
        final byte[] stored = bodyFile(a, r, s);
        // enough for A alone: R's rebuild holds A beside its own first byte, so R cannot be written whole, and S can
        // only if it does not lean on R
        final BodyStore tight = new BodyStore(bodyA.getBytes(StandardCharsets.UTF_8).length);
        tight.add(SessionDataFile.parse(stored));
        final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), tight).getBytes();
        assertFalse(new String(served, StandardCharsets.UTF_8).contains(SECRET));
        final SessionDataFile after = SessionDataFile.parse(served);
        for (final SessionDataFile.Record rec : after.getRecords()) {
            final Manifest m = Manifest.of(rec);
            if (m == null) {
                // withheld whole: every part omitted, nothing refers to it any more
                for (final SessionDataFile.Part p : rec.getParts()) {
                    assertEquals("omitted", p.getState(), rec.getId());
                }
                continue;
            }
            // whatever is served with a manifest rebuilds from the served file alone
            for (final Manifest.Segment seg : m.getSegments()) {
                assertTrue(seg.getPiece() == null && seg.getCopyFrom() == null, rec.getId() + " refers to another body");
            }
        }
    }

    /** A tool keeps its name where it wraps one object that holds it, as OpenAI's function and Bedrock's toolSpec do. */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\",\"parameters\":{}}}]}",
        "{\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}],\"toolConfig\":{\"tools\":[{\"toolSpec\":{\"name\":\"t\",\"description\":\"" + TOOL_SECRET + "\"}}]}}",
    })
    public void aToolKeepsTheNameOfTheObjectItWraps(final String body) {
        final byte[] stored = bodyFile(request("r1", body, Collections.emptyList()));
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.TOOL_SCHEMAS), store).getBytes();
        final BodyStore again = new BodyStore();
        again.add(SessionDataFile.parse(served));
        final String masked = assertRebuilds(again, "r1");
        assertFalse(masked.contains(TOOL_SECRET), masked);
        assertTrue(masked.contains("{\"name\":\"t\",\"withheld\":\"tool_schemas\"}"), masked);
    }

    /**
     * A chain of responses as long as a session makes it, each using a piece of the one before, served from a later
     * file with nothing of the chain decided yet: the walk keeps a stack of its own, so it is served as stored on a
     * thread whose stack a walk by recursion would overflow.
     */
    @Test
    public void aLongChainOfResponsesIsServedAsStored() throws Exception {
        final int length = 5000;
        final String[] chain = new String[length];
        String previous = null;
        for (int i = 0; i < length; i++) {
            final String own = "\"p" + i + "\"";
            if (previous == null) {
                chain[i] = record("r" + i, Manifest.ROLE_RESPONSE, "[" + own + "]", Collections.singletonList(own),
                                  segments(lit("["), "{\"part\":0}", lit("]")));
            } else {
                chain[i] = record("r" + i, Manifest.ROLE_RESPONSE, "[" + previous + "," + own + "]", Collections.singletonList(own),
                                  segments(lit("["), piece(previous), lit(","), "{\"part\":0}", lit("]")));
            }
            previous = own;
        }
        final byte[] earlier = bodyFile(chain);
        final byte[] later = bodyFile(record("last", Manifest.ROLE_RESPONSE, "[" + previous + "]", Collections.emptyList(),
                                             segments(lit("["), piece(previous), lit("]"))));
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(earlier));
        store.add(SessionDataFile.parse(later));
        final Withholding.Served[] served = new Withholding.Served[1];
        final Throwable[] failed = new Throwable[1];
        final Thread small = new Thread(null, () -> {
            try {
                served[0] = Withholding.file(later, BOTH, store);
            } catch (final Throwable e) {
                failed[0] = e;
            }
        }, "small-stack", 256 * 1024);
        small.start();
        small.join();
        assertNull(failed[0], "the walk overflowed its thread's stack");
        assertFalse(served[0].isChanged(), "a response that leans on no request is served as stored");
        assertEquals(later, served[0].getBytes());
    }

    /**
     * A stored file whose closing line does not check is served with that line, so a reader refuses it as it refuses
     * the stored one; a closing line computed here would make a damaged file read as a whole one.
     */
    @Test
    public void aClosingLineThatDoesNotCheckStillDoesNot() {
        final String named = "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"n\",\"flags\":[\"injected\",\"system_prompt\"],"
            + "\"parts\":[{\"k\":\"text\",\"text\":\"" + SECRET + "\",\"state\":\"available\",\"bytes\":32}]}";
        final String whole = new String(file("transcript", named), StandardCharsets.UTF_8);
        final String closing = whole.substring(whole.lastIndexOf("{\"t\":\"end\""));
        final String damaged = whole.replace("\"bytes\":32}", "\"bytes\":33}");
        for (final boolean intact : new boolean[] {true, false}) {
            final byte[] stored = (intact ? whole : damaged).getBytes(StandardCharsets.UTF_8);
            final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), null).getBytes();
            final String text = new String(served, StandardCharsets.UTF_8);
            assertFalse(text.contains(SECRET), text);
            final String before = text.substring(0, text.lastIndexOf("{\"t\":\"end\""));
            final String declared = SessionDataFile.parse(served).getDeclaredDigest();
            assertEquals(intact, Digests.sha256Hex(before.getBytes(StandardCharsets.UTF_8)).equals(declared),
                         intact ? "a file that checks is served checking" : "a damaged file is served damaged");
            if (!intact) {
                assertTrue(text.endsWith(closing), "the stored closing line stays: " + text);
            }
        }
    }

    /** A record of a provider_body file with a manifest of a schema this reader does not know is withheld whole. */
    @Test
    public void aRecordWithNoManifestThisReaderKnowsIsWithheldWhole() {
        final String body = "{\"system\":\"" + SECRET + "\"}";
        final String later = request("r1", body, Collections.emptyList()).replace("provider_body/1", "provider_body/2");
        final byte[] stored = bodyFile(later);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final byte[] served = Withholding.file(stored, Collections.singletonList(Hide.TOOL_SCHEMAS), store).getBytes();
        final String text = new String(served, StandardCharsets.UTF_8);
        assertFalse(text.contains(SECRET), text);
        for (final SessionDataFile.Part p : SessionDataFile.parse(served).getRecords().get(0).getParts()) {
            assertEquals("omitted", p.getState());
        }
    }

    /**
     * A request the Sessionizer kept whole, in one unknown part with a why, rebuilds from that part's string, as
     * plain text or as base64, and is masked like any other; the part, which holds the whole body, goes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void aRequestKeptWholeInAnUnknownPartIsMasked(final boolean base64) {
        final String body = "{\"system\":\"" + SECRET + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + PERSON + "\"}]}";
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        final JsonObject unknown = new JsonObject();
        unknown.addProperty("k", "unknown");
        unknown.addProperty("text", "it did not come back whole");
        unknown.addProperty("data", base64 ? Base64.getEncoder().encodeToString(bytes) : body);
        if (base64) {
            unknown.addProperty("encoding", "base64");
        }
        unknown.addProperty("state", "available");
        unknown.addProperty("bytes", bytes.length);
        final String manifest = "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"src\":\"r1.json\",\"sha256\":\""
            + Digests.sha256Hex(bytes) + "\",\"bytes\":" + bytes.length + ",\"depth\":0,\"why\":\"it did not come back whole\",\"segments\":[{\"part\":0}]}";
        final String rec = "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":" + bytes.length + ",\"id\":\"r1\",\"parts\":[" + unknown
            + ",{\"k\":\"data\",\"data\":" + manifest + ",\"state\":\"available\",\"bytes\":" + manifest.length() + "}]}";
        final byte[] stored = bodyFile(rec);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        assertEquals(body, assertRebuilds(store, "r1"));
        final String served = new String(Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), store).getBytes(), StandardCharsets.UTF_8);
        assertFalse(served.contains(SECRET), served);
        assertFalse(served.contains(Base64.getEncoder().encodeToString(bytes)), served);
        final BodyStore again = new BodyStore();
        again.add(SessionDataFile.parse(served.getBytes(StandardCharsets.UTF_8)));
        final String masked = assertRebuilds(again, "r1");
        assertTrue(masked.contains("[withheld: system_prompt]") && masked.contains(PERSON), masked);
    }

    private static String piece(final String bytes) {
        return "{\"piece\":\"" + Digests.sha256Hex(bytes.getBytes(StandardCharsets.UTF_8)) + "\"}";
    }

}
