package org.apache.skywalking.oap.server.core.analysis.indicator;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorFunction;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author liuhaoyang
 **/
@IndicatorFunction(functionName = "max")
public abstract class MaxIndicator extends Indicator implements LongValueHolder {

    protected static final String VALUE = "value";

    @Getter @Setter @Column(columnName = VALUE, isValue = true) private long value;

    @Entrance
    public final void combine(@SourceFrom long count) {
        if (count > this.value) {
            this.value = count;
        }
    }

    @Override public final void combine(Indicator indicator) {
        MaxIndicator countIndicator = (MaxIndicator)indicator;
        combine(countIndicator.value);
    }

    @Override public void calculate() {
    }

    @Override public long getValue() {
        return value;
    }
}
