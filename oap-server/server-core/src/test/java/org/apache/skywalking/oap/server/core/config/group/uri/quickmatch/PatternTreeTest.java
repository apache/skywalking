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

package org.apache.skywalking.oap.server.core.config.group.uri.quickmatch;

import java.lang.reflect.Field;
import java.util.List;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PatternTreeTest {
    @Test
    public void testTreeBuild() throws NoSuchFieldException, IllegalAccessException {
        PatternTree tree = new PatternTree();
        tree.addPattern("/products/{var}");
        tree.addPattern("/products/{var}/detail");
        tree.addPattern("/products/{var}/refund");
        tree.addPattern("/products/{var}/reorder/extra");
        tree.addPattern("/sales/{var}");
        tree.addPattern("/employees/{var}/profile");
        // This should map to exact same tree nodes
        tree.addPattern("produces/{var}/profile");
        tree.addPattern("GET:/posts/{var}");
        tree.addPattern("https://abc.com/posts/{var}");

        final Field rootField = PatternTree.class.getDeclaredField("roots");
        rootField.setAccessible(true);
        final List<PatternToken> roots = (List<PatternToken>) rootField.get(tree);

        final PatternToken prodToken = roots.get(0);
        Assertions.assertEquals(new StringToken("products"), prodToken);
        Assertions.assertEquals(1, prodToken.children().size());
        final PatternToken varToken = prodToken.children().get(0);
        Assertions.assertEquals(new VarToken(), varToken);
        Assertions.assertEquals(3, varToken.children().size());
        final PatternToken detailToken = varToken.children().get(0);
        Assertions.assertEquals(new StringToken("detail"), detailToken);

        final PatternToken salesToken = roots.get(1);
        Assertions.assertEquals(new StringToken("sales"), salesToken);
        Assertions.assertEquals(1, salesToken.children().size());

        final PatternToken employeeToken = roots.get(2);
        Assertions.assertEquals(new StringToken("employees"), employeeToken);
        Assertions.assertEquals(1, employeeToken.children().size());

        final PatternToken producesToken = roots.get(3);
        Assertions.assertEquals(new StringToken("produces"), producesToken);
        Assertions.assertEquals(1, producesToken.children().size());

        final PatternToken getPostsToken = roots.get(4);
        Assertions.assertEquals(new StringToken("GET:"), getPostsToken);

        final PatternToken abcToken = roots.get(5);
        Assertions.assertEquals(new StringToken("https:"), abcToken);
        final PatternToken abcComToken = abcToken.children().get(0);
        // For general performance purposes, double / will result in an empty string token
        // This is considered an intentional feature rather than a bug
        Assertions.assertEquals(new StringToken(""), abcComToken);
    }

    @Test
    public void testPatternMatch() {
        PatternTree tree = new PatternTree();
        tree.addPattern("/products/{var}");
        tree.addPattern("/products/{var}/detail");
        tree.addPattern("/products/{var}/refund");
        tree.addPattern("/products/{var}/reorder/extra");
        tree.addPattern("/sales/{var}");
        tree.addPattern("/employees/{var}/profile");
        tree.addPattern("produces/{var}/profile");
        tree.addPattern("GET:/posts/{var}");
        tree.addPattern("https://abc.com/posts/{var}");

        StringFormatGroup.FormatResult result;
        result = tree.match("/products/123");
        Assertions.assertTrue(result.isMatch());
        Assertions.assertEquals("/products/{var}", result.getReplacedName());

        result = tree.match("/products/123/detail");
        Assertions.assertTrue(result.isMatch());
        Assertions.assertEquals("/products/{var}/detail", result.getReplacedName());

        result = tree.match("/employees/skywalking/profile");
        Assertions.assertTrue(result.isMatch());

        // URI doesn't have / as prefix but should still match
        result = tree.match("products/123/detail");
        Assertions.assertTrue(result.isMatch());

        // URI has / as suffix but should still match
        result = tree.match("products/123/detail/");
        Assertions.assertTrue(result.isMatch());

        // URI shorter than pattern
        result = tree.match("/products/123/reorder");
        Assertions.assertFalse(result.isMatch());
        Assertions.assertEquals("/products/123/reorder", result.getReplacedName());

        // URI has extra suffix
        result = tree.match("/products/123/detail/extra");
        Assertions.assertFalse(result.isMatch());
        Assertions.assertEquals("/products/123/detail/extra", result.getReplacedName());

        // Domain style URI
        result = tree.match("https://abc.com/posts/123abc");
        Assertions.assertTrue(result.isMatch());

        // Special case: When endpoint is like /abc or abc,
        // it will always match since itself cannot be a variable
        // regardless if abc is actually in the pattern tree
        result = tree.match("/abc");
        Assertions.assertTrue(result.isMatch());
        Assertions.assertEquals("/abc", result.getReplacedName());
    }

    @Test
    public void testGetPostPatternMatch() {
        PatternTree tree = new PatternTree();
        tree.addPattern("GET:/products/{var}");
        tree.addPattern("POST:/products/{var}/detail");
        tree.addPattern("POST:/sales/{var}");
        tree.addPattern("GET:/employees/{var}/profile");

        StringFormatGroup.FormatResult result;
        result = tree.match("GET:/products/123");
        Assertions.assertTrue(result.isMatch());
        Assertions.assertEquals("GET:/products/{var}", result.getReplacedName());
    }
}