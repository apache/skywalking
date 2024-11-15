/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.type;

import lombok.Data;

@Data
public class Arguments {
    private boolean threads;
    private boolean classify;
}
