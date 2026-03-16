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

package org.apache.skywalking.e2e.controller;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.Instant;

@RestController
@RequestMapping("/llm")
public class LLMMockController {
    @PostMapping("/v1/chat/completions")
    public Object completions(@RequestBody JSONObject request, HttpServletResponse response) throws Exception {

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        String id = "chatcmpl-simple-mock-001";
        long created = Instant.now().getEpochSecond();
        String model = "gpt-4.1-mini-2025-04-14";

        try (PrintWriter writer = response.getWriter()) {
            Thread.sleep(1000);
            writeStreamChunk(writer, id, created, model, "{\"role\":\"assistant\"}", "null");

            String fullContent = "Why did the scarecrow win an award? Because he was outstanding in his field!";
            String[] words = fullContent.split(" ");

            for (int i = 0; i < words.length; i++) {
                String chunk = words[i] + (i == words.length - 1 ? "" : " ");
                Thread.sleep(50);
                writeStreamChunk(writer, id, created, model, "{\"content\":\"" + chunk + "\"}", "null");
            }

            writeStreamChunk(writer, id, created, model, "{}", "\"stop\"");

            writer.write("data: [DONE]\n\n");
            writer.flush();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void writeStreamChunk(PrintWriter writer, String id, long created, String model, String delta, String finishReason) {
        String json = "{"
                + "\"id\": \"%s\","
                + "\"object\": \"chat.completion.chunk\","
                + "\"created\": %d,"
                + "\"model\": \"%s\","
                + "\"system_fingerprint\": null,"
                + "\"choices\": ["
                + "{"
                + "\"index\": 0,"
                + "\"delta\": %s,"
                + "\"finish_reason\": %s"
                + "}"
                + "],"
                + "\"usage\": {"
                + "\"completion_tokens\": 17,"
                + "\"completion_tokens_details\": {"
                + "\"accepted_prediction_tokens\": 0,"
                + "\"audio_tokens\": 0,"
                + "\"reasoning_tokens\": 0,"
                + "\"rejected_prediction_tokens\": 0"
                + "},"
                + "\"prompt_tokens\": 52,"
                + "\"prompt_tokens_details\": {"
                + "\"audio_tokens\": 0,"
                + "\"cached_tokens\": 0"
                + "},"
                + "\"total_tokens\": 69"
                + "}"
                + "}";

        String formattedJson = String.format(json, id, created, model, delta, finishReason);

        String cleanJson = formattedJson.replace("\n", "").replace("\r", "");
        writer.write("data: " + cleanJson + "\n\n");
        writer.flush();
    }
}
