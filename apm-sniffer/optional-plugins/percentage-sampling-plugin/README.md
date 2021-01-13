# Overview

This plugin adds another sampling mechanism which determines if it should "sample"
according to a specified rate parameter instead of the original `agent.sample_n_per_3_secs`.

# Usage

1. To enable this plugin, drop the apm-percentage-sampling-plugin-<version>.jar into plugins folder
2. Add a config to agentArgs using key `plugin.sampling.sample_rate` to sample n out of 10000.
For example `plugin.sampling.sample_rate = 100` means 1% (100 out of 10000). Do note that:
    - because PRNG is involved, the actual result will not be precisely the specified amount.
    - just like other plugins, config can be given using system properties and environment variables.
