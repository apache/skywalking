#How to use trace ignore plugin
Please copy the apm-trace-ignore-plugin-x.jar to `agent/plugins`

## How to set config 
 1. This plugin support reading config from environment variables(The env key must start with `skywalking.`, the reuslt should be as same as in `apm-trace-ignore-plugin.config`)
 2. Or you can copy the `apm-trace-ignore-plugin.config` to `agent/config` then you'll set you need ignore paths in `apm-trace-ignore-plugin.config`
