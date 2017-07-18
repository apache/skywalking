package org.skywalking.apm.collector.core.cluster;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.Listener;

/**
 * @author pengys5
 */
public abstract class ClusterDataListener implements Listener {

    private final String moduleName;
    private List<Data> datas;

    public ClusterDataListener(String moduleName) {
        this.moduleName = moduleName;
        datas = new LinkedList<>();
    }

    public final String moduleName() {
        return moduleName;
    }

    public abstract String path();

    public final void setData(Data data) {
        datas.add(data);
    }

    public static class Data {
        private final String key;
        private final String value;

        public Data(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
