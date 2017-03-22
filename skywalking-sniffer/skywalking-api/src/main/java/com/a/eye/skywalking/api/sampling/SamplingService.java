package com.a.eye.skywalking.api.sampling;

import com.a.eye.skywalking.api.boot.BootService;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * The <code>SamplingService</code> take charge of how to sample the {@link TraceSegment}. Every {@link TraceSegment}s
 * have been traced, but, considering CPU cost of serialization/deserialization, and network bandwidth, the agent do NOT
 * send all of them to collector, if SAMPLING is on.
 *
 * By default, SAMPLING is off, and {@link Config.Agent#SAMPLING_RATE} == 1000.
 *
 * @author wusheng
 */
public class SamplingService implements BootService {
    private static ILog logger = LogManager.getLogger(SamplingService.class);

    private volatile boolean on = false;
    private volatile int rate = 0;
    private volatile int rollingSeed = 1;

    @Override
    public void bootUp() throws Throwable {
        if (Config.Agent.SAMPLING_RATE == 10000) {
            return;
        }
        if (Config.Agent.SAMPLING_RATE > 10000 || Config.Agent.SAMPLING_RATE < 1) {
            throw new IllegalSamplingRateException("sampling rate should stay in (0, 10000].");
        }
        rate = 10000 / Config.Agent.SAMPLING_RATE;
        on = true;

        logger.debug("The trace sampling is on, and the sampling rate is: {}", rate);
    }

    public void trySampling(TraceSegment segment) {
        if (on) {
            if (rollingSeed % rate != 0) {
                segment.setSampled(false);
            }
            rollingSeed++;
        }
    }

    /**
     * Set the {@link TraceSegment} to sampled, when {@link ContextCarrier} contains "isSampled" flag.
     *
     * A -> B, if TraceSegment is sampled in A, then the related TraceSegment in B must be sampled, no matter you
     * sampling rate. And reset the {@link #rollingSeed}, in case of too many {@link TraceSegment}s, which started in
     * this JVM, are sampled.
     *
     * @param segment the current TraceSegment.
     * @param carrier
     */
    public void setSampleWhenExtract(TraceSegment segment, ContextCarrier carrier) {
        if(on) {
            if (!segment.isSampled() && carrier.isSampled()) {
                segment.setSampled(true);
                this.rollingSeed = 1;
            }
        }
    }

}
