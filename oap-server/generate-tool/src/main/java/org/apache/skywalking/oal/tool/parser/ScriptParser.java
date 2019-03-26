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

package org.apache.skywalking.oal.tool.parser;

import java.io.IOException;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.apache.skywalking.oal.tool.grammar.*;

public class ScriptParser {
    private OALLexer lexer;

    private ScriptParser() {

    }

    public static ScriptParser createFromFile(String scriptFilepath) throws IOException {
        ScriptParser parser = new ScriptParser();
        parser.lexer = new OALLexer(CharStreams.fromFileName(scriptFilepath));
        return parser;
    }

    public static ScriptParser createFromScriptText(String script) throws IOException {
        ScriptParser parser = new ScriptParser();
        parser.lexer = new OALLexer(CharStreams.fromString(script));
        return parser;
    }

    public OALScripts parse() throws IOException {
        OALScripts scripts = new OALScripts();

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        OALParser parser = new OALParser(tokens);

        ParseTree tree = parser.root();
        ParseTreeWalker walker = new ParseTreeWalker();

        walker.walk(new OALListener(scripts), tree);

        return scripts;
    }

    public void close() {
    }
}
