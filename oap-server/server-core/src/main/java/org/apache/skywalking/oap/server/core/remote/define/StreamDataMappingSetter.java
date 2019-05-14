package org.apache.skywalking.oap.server.core.remote.define;

import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * @author peng-yongsheng
 */
public interface StreamDataMappingSetter extends Service {
    void putIfAbsent(Class<? extends StreamData> streamDataClass);
}
