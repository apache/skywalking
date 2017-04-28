package org.skywalking.apm.agent.core.sampling;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.trace.TraceSegment;

/**
 * The <code>SamplingService</code> take charge of how to sample the {@link TraceSegment}. Every {@link TraceSegment}s
 * have been traced, but, considering CPU cost of serialization/deserialization, and network bandwidth, the agent do NOT
 * send all of them to collector, if SAMPLING is on.
 * <p>
 * By default, SAMPLING is off, and {@link Config.Agent#SAMPLING_CYCLE} == 1.
 *
 * @author wusheng
 */
public class SamplingService implements BootService {
    private static final ILog logger = LogManager.getLogger(SamplingService.class);

    private volatile boolean on = false;
    private volatile int rollingSeed = 1;

    @Override
    public void bootUp() throws Throwable {
        if (Config.Agent.SAMPLING_CYCLE == 1) {
            this.on = false;
            return;
        }
        if (Config.Agent.SAMPLING_CYCLE < 1) {
            throw new IllegalSamplingRateException("sampling cycle must greater than 0.");
        }
        this.on = true;

        logger.debug("The trace sampling is on, and the sampling cycle is: {}", Config.Agent.SAMPLING_CYCLE);
    }

    public void trySampling(TraceSegment segment) {
        if (on) {
            if (rollingSeed % Config.Agent.SAMPLING_CYCLE != 0) {
                segment.setSampled(false);
                this.rollingSeed = 1;
            }
            rollingSeed++;
        }
    }

    /**
     * Set the {@link TraceSegment} to sampled, when {@link ContextCarrier} contains "isSampled" flag.
     * <p>
     * A -> B, if TraceSegment is sampled in A, then the related TraceSegment in B must be sampled, no matter you
     * sampling rate. And reset the {@link #rollingSeed}, in case of too many {@link TraceSegment}s, which started in
     * this JVM, are sampled.
     *
     * @param segment the current TraceSegment.
     * @param carrier
     */
    public void setSampleWhenExtract(TraceSegment segment, ContextCarrier carrier) {
        if (on) {
            if (!segment.isSampled() && carrier.isSampled()) {
                segment.setSampled(true);
                this.rollingSeed = 1;
            }
        }
    }

}
