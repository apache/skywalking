# Uninstrumented Gateways/Proxies

The uninstrumented gateways are not instrumented by the SkyWalking agent plugin when they start,
but they can be configured in `gateways.yml` file or via [Dynamic Configuration](dynamic-config.md). The reason why they can't register
to backend automatically is that there are no suitable agent plugins. For example, there are no agent plugins for Nginx, HAProxy, etc.
So to visualize the real topology, we provide a way to configure the gateways/proxies manually.

## Configuration Format

The configuration content includes gateway names and their instances:

```yml
gateways:
 - name: proxy0 # the name is not used for now
   instances:
     - host: 127.0.0.1 # the host/IP of this gateway instance
       port: 9099 # the port of this gateway instance defaults to 80
```

**Note**: The `host` of the instance must be the one that is actually used on the client-side. For example,
if instance `proxyA` has 2 IPs, say `192.168.1.110` and `192.168.1.111`, both of which delegate the target service,
and the client connects to `192.168.1.110`, then configuring `192.168.1.111` as the `host` won't work properly.
