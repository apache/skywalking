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

package org.apache.skywalking.oap.server.core.storage.management;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * Per-backend read / write / delete DAO for runtime-managed MAL/LAL rule files. The generic
 * {@link org.apache.skywalking.oap.server.core.storage.IManagementDAO#insert} path is not
 * used: BanyanDB's generic impl never persists, and ES/JDBC short-circuit when the row
 * already exists, which silently breaks {@code /addOrUpdate} and {@code /inactivate} (every
 * call after the first becomes a no-op). Each backend implements upsert semantics directly
 * here so the persist-is-commit invariant holds across all three.
 */
public interface RuntimeRuleManagementDAO extends DAO {

    /**
     * @return every runtime-rule file, ACTIVE and INACTIVE alike. The reconciler is the sole
     *         caller today; it diffs the full set against its in-memory snapshot on every tick.
     */
    List<RuntimeRuleFile> getAll() throws IOException;

    /**
     * Upsert by composite key (catalog, name). Replaces {@code content}, {@code status} and
     * {@code updateTime} when the row already exists; inserts a new row otherwise. Both
     * {@code /addOrUpdate} (every call after the first) and {@code /inactivate} (every call)
     * depend on overwrite semantics — without it the operator sees 200 / structural_applied
     * / inactivated while the backing row stays unchanged.
     *
     * @throws IOException when the underlying storage write fails. Callers translate this
     *                     into a 5xx response so the operator does not get a false success.
     */
    void save(RuntimeRule rule) throws IOException;

    /**
     * Hard delete a runtime-rule file by composite key. Idempotent: if no record matches,
     * implementations must return silently rather than throw. A successful return does not
     * imply the backend physically reclaimed the storage (e.g. Elasticsearch may mark it
     * deleted pending a merge); it only implies the file will not be returned by
     * {@link #getAll()}.
     */
    void delete(String catalog, String name) throws IOException;

    /**
     * Logical representation of one runtime-managed rule file (YAML content + status +
     * update time, keyed by catalog + name). Lives in server-core to avoid a module
     * dependency from storage plugins onto the runtime-rule receiver plugin. Each storage
     * impl populates this from its own result set. Named for the operator-facing concept
     * ("a rule file") rather than the persistence shape ("a storage row").
     */
    class RuntimeRuleFile {
        private final String catalog;
        private final String name;
        private final String content;
        private final String status;
        private final long updateTime;

        public RuntimeRuleFile(final String catalog, final String name, final String content,
                              final String status, final long updateTime) {
            this.catalog = catalog;
            this.name = name;
            this.content = content;
            this.status = status;
            this.updateTime = updateTime;
        }

        public String getCatalog() {
            return catalog;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        public String getStatus() {
            return status;
        }

        public long getUpdateTime() {
            return updateTime;
        }
    }
}
