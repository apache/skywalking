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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.List;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Registry for every {@link Model} the OAP process knows about, plus the event bus
 * ({@link CreatingListener}) that storage installers subscribe to. Every mutation carries
 * a {@link StorageManipulationOpt} so callers can express policy (full-install vs.
 * local-cache-only) and installers can report per-resource outcomes back through the same
 * object.
 */
public interface ModelRegistry extends Service {
    /**
     * Add a new model with a caller-specified {@link StorageManipulationOpt policy}. If a model
     * with the same {@code storage#getModelName()} and {@code storage#getDownsampling()} already
     * exists, the call is treated as idempotent and the existing model is returned without firing
     * {@link CreatingListener#whenCreating(Model, StorageManipulationOpt)} again.
     *
     * <p>The {@code opt} is mutable: installers record per-resource outcomes on it as they run.
     * Callers may inspect {@link StorageManipulationOpt#getOutcomes()} after return.
     *
     * @return the created or pre-existing model
     */
    Model add(Class<?> aClass, int scopeId, Storage storage, StorageManipulationOpt opt)
        throws StorageException;

    /**
     * Remove an existing model by its stream class with a caller-specified policy. All models
     * registered through {@link #add(Class, int, Storage, StorageManipulationOpt)} with the given
     * stream class (across any downsampling variants) are removed from the registry, and every
     * registered {@link CreatingListener#whenRemoving(Model, StorageManipulationOpt)} is fired for
     * each. Used by runtime rule hot-update (MAL/LAL hot-remove); not intended to be called during
     * the startup path.
     *
     * <p>Peer-node callers pass {@link StorageManipulationOpt#localCacheOnly()} so installers
     * skip the server-side drop and record {@link StorageManipulationOpt.Outcome#SKIPPED_NOT_ALLOWED}
     * against the affected resources.
     *
     * @return the list of models that were removed, empty if none matched
     */
    List<Model> remove(Class<?> streamClass, StorageManipulationOpt opt) throws StorageException;

    void addModelListener(CreatingListener listener) throws StorageException;

    interface CreatingListener {
        /**
         * Invoked when a model is registered via {@link ModelRegistry#add}. Listeners receive
         * the {@link StorageManipulationOpt} the caller threaded through the registry — skip
         * server-side DDL when {@link StorageManipulationOpt#isLocalCacheOnly()}, and record
         * per-resource outcomes on the opt for the caller to inspect.
         */
        void whenCreating(Model model, StorageManipulationOpt opt) throws StorageException;

        /**
         * Invoked when a model is removed via {@link ModelRegistry#remove}. Default is a no-op
         * so listeners that don't own server-side resources (e.g., pure schema caches) compile
         * without boilerplate. Storage installers that own physical schema (BanyanDB measures)
         * override this and skip the server-side drop when
         * {@link StorageManipulationOpt#isLocalCacheOnly()}.
         */
        default void whenRemoving(Model model, StorageManipulationOpt opt) throws StorageException {
        }
    }
}
