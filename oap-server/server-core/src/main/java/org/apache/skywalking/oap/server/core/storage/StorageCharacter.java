package org.apache.skywalking.oap.server.core.storage;

import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * StorageCharacter provides core aware characters which make the core could run optimized codes accordingly.
 *
 * @since 9.5.0
 */
public interface StorageCharacter extends Service {
    /**
     * See {@link ScopeDefaultColumn.DefinedByField#idxOfCompositeID()}
     *
     * @return true if ID is declared through existing column, but not a virtual column. Typically, there was an entity_id
     * column to represent a subject(service, endpoint, et.c)
     */
    boolean supportCompositeID();

    class Default implements StorageCharacter {

        @Override
        public boolean supportCompositeID() {
            return false;
        }
    }
}
