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

package org.apache.skywalking.oap.server.ai.agent.conversation.view;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Writes an <code>asz.view</code> document as YAML: block style, two-space indent, keys in insertion order, no
 * anchors and no type tags, so the same document gives the same bytes wherever it is rendered.
 */
public final class ViewYaml {
    public static final String FORMAT = "asz.view";
    public static final String VERSION = "1.0";

    private ViewYaml() {
    }

    /**
     * @param document the document, as ordered maps, lists, strings, numbers and booleans
     * @return the YAML text
     */
    public static String dump(final Map<String, Object> document) {
        final StringWriter out = new StringWriter();
        write(document, out);
        return out.toString();
    }

    /**
     * Streams the document into the writer as it is walked; nothing is buffered whole.
     *
     * @param document the document, as ordered maps, lists, strings, numbers and booleans
     * @param out      where the YAML goes
     */
    public static void write(final Map<String, Object> document, final Writer out) {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(false);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setAllowUnicode(true);
        final Representer representer = new Representer(options);
        final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()), representer, options);
        yaml.dump(document, out);
    }
}
