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

package org.apache.skywalking.library.banyandb.v1.client.grpc;

import com.google.protobuf.GeneratedMessage;
import io.grpc.stub.AbstractBlockingStub;
import java.util.List;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.ResourceExist;

/**
 * abstract metadata client which defines CRUD operations for a specific kind of schema.
 *
 * @param <P> ProtoBuf: schema defined in ProtoBuf format
 */
public abstract class MetadataClient<STUB extends AbstractBlockingStub<STUB>, P extends GeneratedMessage> {
    public static final long DEFAULT_MOD_REVISION = 0;

    protected final STUB stub;

    protected MetadataClient(STUB stub) {
        this.stub = stub;
    }

    /**
     * Create a schema
     *
     * @param payload the schema to be created
     * @return the mod revision of the schema
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract long create(P payload) throws BanyanDBException;

    /**
     * Update the schema
     *
     * @param payload the schema which will be updated with the given name and group
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract void update(P payload) throws BanyanDBException;

    /**
     * Update the schema and return the etcd {@code mod_revision} stamped on the
     * server-side write. Callers that need to fence subsequent data writes / queries
     * against the new shape (via {@code SchemaBarrierService.AwaitRevisionApplied})
     * use this overload to capture the revision; callers that don't need the fence
     * can still use {@link #update(GeneratedMessage)}.
     *
     * <p>Default implementation calls {@link #update(GeneratedMessage)} and returns
     * {@link #DEFAULT_MOD_REVISION} (0) — registries that do not yet expose
     * {@code mod_revision} on their Update response keep the no-fence behaviour.
     * Concrete subclasses override to read {@code mod_revision} off the typed
     * response.
     */
    public long updateWithRevision(P payload) throws BanyanDBException {
        update(payload);
        return DEFAULT_MOD_REVISION;
    }

    /**
     * Delete a schema
     *
     * @param group the group of the schema to be removed
     * @param name  the name of the schema to be removed
     * @return whether this schema is deleted
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract boolean delete(String group, String name) throws BanyanDBException;

    /**
     * Delete a schema and return the etcd {@code mod_revision} of the tombstone.
     * Returns {@link #DEFAULT_MOD_REVISION} (0) when the server did not record a
     * tombstone — callers that need a delete-fence then fall back to
     * {@code SchemaBarrierService.AwaitSchemaDeleted} keyed on the resource.
     *
     * <p>Default implementation calls {@link #delete(String, String)} and returns
     * {@link #DEFAULT_MOD_REVISION}; concrete subclasses override to read
     * {@code mod_revision} off the typed response.
     */
    public long deleteWithRevision(String group, String name) throws BanyanDBException {
        delete(group, name);
        return DEFAULT_MOD_REVISION;
    }

    /**
     * Get a schema with name
     *
     * @param group the group of the schema to be found
     * @param name  the name of the schema to be found
     * @return the schema, null if not found
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract P get(String group, String name) throws BanyanDBException;

    /**
     * Check whether a schema exists
     *
     * @param group the group of the schema to be found
     * @param name the name of the schema to be found
     * @return whether resource exists
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public  abstract ResourceExist exist(String group, String name) throws BanyanDBException;

    /**
     * List all schemas with the same group name
     *
     * @return a list of schemas found
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract List<P> list(String group) throws BanyanDBException;

    protected <REQ, RESP, E extends BanyanDBException> RESP execute(HandleExceptionsWith.SupplierWithIO<RESP, E> supplier) throws BanyanDBException {
        return HandleExceptionsWith.callAndTranslateApiException(supplier);
    }
}
