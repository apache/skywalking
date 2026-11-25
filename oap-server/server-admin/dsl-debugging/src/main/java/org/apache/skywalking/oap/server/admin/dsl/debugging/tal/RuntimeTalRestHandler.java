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

package org.apache.skywalking.oap.server.admin.dsl.debugging.tal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TalStaticBindingHook;

/**
 * Read-only listing of the TAL rules this node loaded, for the rule view and the debugger's rule picker. TAL rules
 * load at boot only, so the listing is what the node runs until it restarts. Pure metadata: it does not need the
 * debug probes.
 */
@Blocking
public class RuntimeTalRestHandler {

    private static final Gson GSON = new Gson();

    private final TALHolderRegistry registry;
    /**
     * Whether this node takes sessions: the GraalVM distro compiles every rule with its probes, so a rule may carry a
     * holder while capture is turned off.
     */
    private final boolean injectionEnabled;

    public RuntimeTalRestHandler(final TALHolderRegistry registry, final boolean injectionEnabled) {
        this.registry = registry;
        this.injectionEnabled = injectionEnabled;
    }

    /**
     * Every loaded file with its rules: the {@code talFiles} first, then the native files in the order their rules
     * run.
     */
    @Get("/runtime/tal/files")
    public HttpResponse listFiles() {
        final Map<String, JsonObject> files = new LinkedHashMap<>();
        for (final TalStaticBindingHook.Binding binding : registry.bindings()) {
            final String name = binding.getName();
            final JsonObject file = files.computeIfAbsent(name, key -> {
                final JsonObject entry = new JsonObject();
                entry.addProperty("name", key);
                entry.addProperty("list", binding.getList());
                entry.addProperty("inputType", binding.getInputType());
                entry.add("rules", new JsonArray());
                return entry;
            });
            final JsonObject rule = new JsonObject();
            rule.addProperty("name", binding.getRuleName());
            rule.addProperty("layer", binding.getLayer());
            rule.addProperty("inputType", binding.getInputType());
            rule.addProperty("line", binding.getLine());
            if (binding.getOrder() >= 0) {
                rule.addProperty("order", binding.getOrder());
            }
            rule.addProperty("debuggable", injectionEnabled && binding.getHolder() != null
                && registry.lookup(new RuleKey(Catalog.TAL, binding.getName(), binding.getRuleName()))
                    == binding.getHolder());
            file.getAsJsonArray("rules").add(rule);
        }
        final JsonArray array = new JsonArray();
        files.values().forEach(array::add);
        final JsonObject body = new JsonObject();
        body.add("files", array);
        body.addProperty("count", array.size());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    /**
     * A loaded file's content as this node loaded it.
     */
    @Get("/runtime/tal/files/{name}")
    public HttpResponse getFile(@Param("name") final String name) {
        // The name the listing gives, or the file's own name, extension included.
        final Optional<String> content = registry.bindings().stream()
                                                 .filter(binding -> binding.getName().equals(name)
                                                     || binding.getFile().equals(name))
                                                 .findFirst()
                                                 .flatMap(binding -> TalRuleFiles.content(binding.getName()));
        if (content.isEmpty()) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8, "TAL file not loaded: " + name);
        }
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, content.get());
    }
}
