package org.skywalking.apm.agent.core.dictionary;

/**
 * The <code>PossibleFound</code> represents a value, which may exist or not.
 *
 * @author wusheng
 */
public abstract class PossibleFound {
    private boolean found;
    private int value;

    PossibleFound(int value) {
        this.found = true;
        this.value = value;
    }

    PossibleFound() {
        this.found = false;
    }

    public void doInCondition(Found condition1, NotFound condition2) {
        if (found) {
            condition1.doProcess(value);
        } else {
            condition2.doProcess();
        }
    }

    public Object doInCondition(FoundAndObtain condition1, NotFoundAndObtain condition2) {
        if (found) {
            return condition1.doProcess(value);
        } else {
            return condition2.doProcess();
        }
    }

    public interface Found {
        void doProcess(int value);
    }

    public interface NotFound {
        void doProcess();
    }

    public interface FoundAndObtain {
        Object doProcess(int value);
    }

    public interface NotFoundAndObtain {
        Object doProcess();
    }
}
