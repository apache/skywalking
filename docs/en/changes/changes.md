## 11.2.0

#### Project
* Fix the release script on macOS: the source tarball carried an AppleDouble `._*` entry and `com.apple.provenance` xattr headers for every file (the 11.0.0 source release has them, and GNU tar extracts them as stray `._*` files with a warning per entry), the next-version PR step aborted because the single-branch release clone has no remote-tracking ref for the pushed branch, and the vote email left out the BanyanDB client proto submodule.

#### OAP Server
* Fix the Cilium fetcher dropping or double-reading Cilium nodes. The Peer notify stream, the cluster watcher and the 10-second refresh timer rebuilt the shared node list without a lock, which could open two flow streams for one node or keep a closed node as in use. A node closed when this OAP's share of nodes moved away was reused, still closed, when the share moved back (e.g. after scaling the OAP cluster out and back in), so that node went unread while the assignment lasted. A `Receiver`-role OAP on a ZooKeeper, Consul, Nacos or etcd coordinator never finds itself in the cluster list and, when Cilium nodes and other OAPs were listed, threw on every refresh; it now skips Cilium fetching quietly, as the `Mixed` and `Aggregator` OAPs share all Cilium nodes among themselves.
* Fix the gRPC alarm hook throwing a `NullPointerException` on every alarm until OAP restart: once an alarm fired while no gRPC hook was configured (at boot, or after the hooks were removed through dynamic configuration), adding a gRPC hook later broke it. Also shut down the client of a gRPC hook removed from the configuration.
* Fix the Cilium fetcher silently stopping a Cilium node's flow stream. When a stream failed and was retried (e.g. a Cilium agent restart), the ended stream's thread interrupt stayed registered on its node, so closing that node later (e.g. when the OAP cluster scaled) interrupted whichever stream the pooled thread then ran for another node, and an interrupted stream is treated as closed and never retried. A stream that started just as its node closed was also never interrupted. Each stream now registers before it opens and deregisters when it ends, and a closed node refuses new registrations.
* Fix one gRPC alarm hook with a malformed target, such as `http://host` or `host:port` as the host, stopping every gRPC hook: building its client threw and aborted the whole gRPC callback on each alarm, and a malformed target in the static alarm settings stopped OAP from starting. That hook is now skipped with an error log, at boot too, and a hook whose target becomes malformed keeps its previous client.
* Make the cluster coordinator's watcher list safe for a watcher that registers after the coordinator started, as the Cilium fetcher does: the plain list shared with the coordinator's notify thread could throw a `ConcurrentModificationException` there or miss the new watcher.
* Add the `OTLP_VIRTUAL_GATEWAY` layer: the OTLP trace analysis draws a gateway listed in `gateways.yml` as a node of this layer, the OTLP counterpart of the agent path's `VIRTUAL_GATEWAY`, as it already puts the databases, caches and brokers it conjectures under the `OTLP_VIRTUAL_*` layers. The shipped `tal/otlp-general.yaml` uses it; a copy of the rule file made from an earlier release keeps drawing `VIRTUAL_GATEWAY` until it is updated.

#### UI

#### Documentation
* Document how a release is named in the release guide: a few words that state its key features, used as the GitHub release title `x.y.z - <release name>`, which the guide's `gh release create` example now uses in place of the bare version.
* Update the release guide for the redesigned website and add a release-name section to the GitHub release body.
* Fix the Zabbix receiver doc's links to the Zabbix documentation, which moved in Zabbix 8.0, by pinning them to Zabbix 6.0.


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.2.0)

