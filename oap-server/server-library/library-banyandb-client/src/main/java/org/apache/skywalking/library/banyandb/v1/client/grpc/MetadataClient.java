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

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.stub.AbstractBlockingStub;
import java.util.List;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.ResourceExist;

/**
 * abstract metadata client which defines CRUD operations for a specific kind of schema.
 *
 * @param <P> ProtoBuf: schema defined in ProtoBuf format
 */
public abstract class MetadataClient<STUB extends AbstractBlockingStub<STUB>, P extends GeneratedMessageV3> {
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
     * Delete a schema
     *
     * @param group the group of the schema to be removed
     * @param name  the name of the schema to be removed
     * @return whether this schema is deleted
     * @throws BanyanDBException a wrapped exception to the underlying gRPC calls
     */
    public abstract boolean delete(String group, String name) throws BanyanDBException;

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
