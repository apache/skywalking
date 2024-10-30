/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import java.util.regex.Pattern;

public class Arguments {
    public String title = "Flame Graph";
    public String output;
    public String state;
    public Pattern include;
    public Pattern exclude;
    public int skip;
    public boolean reverse;
    public boolean cpu;
    public boolean wall;
    public boolean alloc;
    public boolean live;
    public boolean lock;
    public boolean threads;
    public boolean classify;
    public boolean total;
    public boolean lines;
    public boolean bci;
    public boolean simple;
    public boolean norm;
    public boolean dot;
    public long from;
    public long to;
}
