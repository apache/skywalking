# Trace ignore enhance
Here is an optional plugin `apm-trace-ignore-plugin`

## Introduce
- It's provided to enhance the tracing ignores.
- You can set multiple paths then these paths will be ignored, means the `agent` won't send the `TraceContext` to `collector`.
- The current matching rule follow `Ant Path Match` , like `/path/*`, `/path/**`, `/path/?`.
- The directory of the plugin provides detailed instructions for use, you can find `README` file in `/agent/optional-plugins/apm-trace-ignore-plugin`
