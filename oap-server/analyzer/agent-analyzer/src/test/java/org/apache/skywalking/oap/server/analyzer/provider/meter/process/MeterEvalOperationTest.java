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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import java.util.function.Consumer;
import org.junit.Test;

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
