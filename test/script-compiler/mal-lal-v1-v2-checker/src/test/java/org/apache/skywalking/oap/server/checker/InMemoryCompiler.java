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

package org.apache.skywalking.oap.server.checker;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles generated Java source code in-memory and loads the resulting class.
 */
public final class InMemoryCompiler {

    private final Path tempDir;
    private final URLClassLoader classLoader;

    public InMemoryCompiler() throws IOException {
        this.tempDir = Files.createTempDirectory("checker-compile-");
        final File srcDir = new File(tempDir.toFile(), "src");
        final File outDir = new File(tempDir.toFile(), "classes");
        srcDir.mkdirs();
        outDir.mkdirs();
        this.classLoader = new URLClassLoader(
            new URL[]{outDir.toURI().toURL()},
            Thread.currentThread().getContextClassLoader()
        );
    }

    /**
     * Compile a single Java source file and return the loaded Class.
     *
     * @param packageName fully qualified package (e.g. "org.apache...rt.mal")
     * @param className   simple class name (e.g. "MalExpr_test")
     * @param sourceCode  the full Java source code
     * @return the loaded Class
     */
    public Class<?> compile(final String packageName, final String className,
                     final String sourceCode) throws Exception {
        final String fqcn = packageName + "." + className;

        final File srcDir = new File(tempDir.toFile(), "src");
        final File outDir = new File(tempDir.toFile(), "classes");
        final File pkgDir = new File(srcDir, packageName.replace('.', File.separatorChar));
        pkgDir.mkdirs();

        final File javaFile = new File(pkgDir, className + ".java");
        Files.writeString(javaFile.toPath(), sourceCode);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available — requires JDK");
        }

        final String classpath = System.getProperty("java.class.path");

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            final Iterable<? extends JavaFileObject> units =
                fm.getJavaFileObjectsFromFiles(List.of(javaFile));

            final List<String> options = Arrays.asList(
                "-d", outDir.getAbsolutePath(),
                "-classpath", classpath
            );

            final java.io.StringWriter errors = new java.io.StringWriter();
            final JavaCompiler.CompilationTask task =
                compiler.getTask(errors, fm, null, options, null, units);

            if (!task.call()) {
                throw new RuntimeException(
                    "Compilation failed for " + fqcn + ":\n" + errors);
            }
        }

        return classLoader.loadClass(fqcn);
    }

    public void close() throws IOException {
        classLoader.close();
        deleteRecursive(tempDir.toFile());
    }

    private static void deleteRecursive(final File file) {
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
