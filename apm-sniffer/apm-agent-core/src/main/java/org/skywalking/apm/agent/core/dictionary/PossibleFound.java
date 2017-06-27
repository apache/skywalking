package org.skywalking.apm.agent.core.dictionary;

/**
 * The <code>PossibleFound</code> represents a value, which may exist or not.
 *
 * @author wusheng
 */
public class PossibleFound {
    private boolean found;
    private int value;

    PossibleFound(int value) {
        this.found = true;
        this.value = value;
    }

    PossibleFound() {
        this.found = false;
    }

    public void ifFound(Setter setter) {
        if (found) {
            setter.set(value);
        }
    }

    public interface Setter {
        void set(int value);
    }
}
