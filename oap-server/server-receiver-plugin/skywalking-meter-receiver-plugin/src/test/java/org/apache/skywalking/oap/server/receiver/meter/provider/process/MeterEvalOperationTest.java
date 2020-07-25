package org.apache.skywalking.oap.server.receiver.meter.provider.process;

import org.junit.Test;

import java.util.function.Consumer;

public class MeterEvalOperationTest {

    @Test
    public void testImplement() {
        final EvalSingleData single = new EvalSingleData();
        // Test unimplemented any method of MeterEvalOperation
        final TestEvalOperation operation = new TestEvalOperation();
        assertUnSupported(operation, op -> op.add(1d));
        assertUnSupported(operation, op -> op.add(single));
        assertUnSupported(operation, op -> op.minus(1d));
        assertUnSupported(operation, op -> op.minus(single));
        assertUnSupported(operation, op -> op.multiply(1d));
        assertUnSupported(operation, op -> op.multiply(single));
        assertUnSupported(operation, op -> op.divide(1d));
        assertUnSupported(operation, op -> op.divide(single));
        assertUnSupported(operation, op -> op.scale(1));
        assertUnSupported(operation, op -> op.irate("PT1S"));
        assertUnSupported(operation, op -> op.rate("PT1S"));
        assertUnSupported(operation, op -> op.increase("PT1S"));
    }

    /**
     * Must throw {@link UnsupportedOperationException}
     */
    private void assertUnSupported(TestEvalOperation operation, Consumer<TestEvalOperation> op) {
        try {
            op.accept(operation);
            throw new IllegalStateException();
        } catch (UnsupportedOperationException e) {
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Unimplemented any method
     */
    private static class TestEvalOperation implements MeterEvalOperation<EvalSingleData> {
    }
}
