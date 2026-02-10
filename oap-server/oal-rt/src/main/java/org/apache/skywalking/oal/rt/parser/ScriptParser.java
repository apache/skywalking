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

package org.apache.skywalking.oal.rt.parser;

import java.io.IOException;
import java.io.Reader;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.skywalking.oal.rt.grammar.OALLexer;
import org.apache.skywalking.oal.rt.grammar.OALParser;

/**
 * Script reader and parser.
 */
public class ScriptParser {
    private OALLexer lexer;

    private String sourcePackage;

    private ScriptParser() {

    }

    public static ScriptParser createFromFile(Reader scriptReader, String sourcePackage) throws IOException {
        ScriptParser parser = new ScriptParser();
        parser.lexer = new OALLexer(CharStreams.fromReader(scriptReader));
        parser.sourcePackage = sourcePackage;
        return parser;
    }

    public static ScriptParser createFromScriptText(String script, String sourcePackage) throws IOException {
        ScriptParser parser = new ScriptParser();
        parser.lexer = new OALLexer(CharStreams.fromString(script));
        parser.sourcePackage = sourcePackage;
        return parser;
    }

    public OALScripts parse() throws IOException {
        OALScripts scripts = new OALScripts();

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        OALParser parser = new OALParser(tokens);

        ParseTree tree = parser.root();
        ParseTreeWalker walker = new ParseTreeWalker();

        walker.walk(new OALListener(scripts, sourcePackage), tree);

        return scripts;
    }

    public void close() {
    }
}
