package org.skywalking.apm.agent.core.context.component;

/**
 * @author wusheng
 */
public abstract class AbstractComponent {
    private int id;
    private String name;

    protected AbstractComponent(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }
}
