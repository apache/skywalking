/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package one.jfr;

import lombok.Data;

@Data
public class Arguments {
    private boolean threads;
    private boolean classify;
    private boolean total;
}
