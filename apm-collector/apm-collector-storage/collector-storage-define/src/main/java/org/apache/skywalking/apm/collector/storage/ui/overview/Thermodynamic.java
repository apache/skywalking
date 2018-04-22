package org.apache.skywalking.apm.collector.storage.ui.overview;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peng-yongsheng
 */
public class Thermodynamic {

    private List<Long> nodes;
    private int responseTimeStep;

    public Thermodynamic() {
        this.nodes = new LinkedList<>();
    }

    public List<Long> getNodes() {
        return nodes;
    }

    public void setNodes(List<Long> nodes) {
        this.nodes = nodes;
    }

    public int getResponseTimeStep() {
        return responseTimeStep;
    }

    public void setResponseTimeStep(int responseTimeStep) {
        this.responseTimeStep = responseTimeStep;
    }
}
