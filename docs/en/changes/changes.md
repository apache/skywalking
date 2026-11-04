## 11.2.0

#### Project
* Fix the release script on macOS: the source tarball carried an AppleDouble `._*` entry and `com.apple.provenance` xattr headers for every file (the 11.0.0 source release has them, and GNU tar extracts them as stray `._*` files with a warning per entry), the next-version PR step aborted because the single-branch release clone has no remote-tracking ref for the pushed branch, and the vote email left out the BanyanDB client proto submodule.

#### OAP Server
* Fix the Cilium fetcher dropping or double-reading Cilium nodes. The Peer notify stream, the cluster watcher and the 10-second refresh timer rebuilt the shared node list without a lock, which could open two flow streams for one node or keep a closed node as in use. A node closed when this OAP's share of nodes moved away was reused, still closed, when the share moved back (e.g. after scaling the OAP cluster out and back in), so that node went unread while the assignment lasted. A `Receiver`-role OAP on a ZooKeeper, Consul, Nacos or etcd coordinator never finds itself in the cluster list and, when Cilium nodes and other OAPs were listed, threw on every refresh; it now skips Cilium fetching quietly, as the `Mixed` and `Aggregator` OAPs share all Cilium nodes among themselves.
* Fix the gRPC alarm hook throwing a `NullPointerException` on every alarm until OAP restart: once an alarm fired while no gRPC hook was configured (at boot, or after the hooks were removed through dynamic configuration), adding a gRPC hook later broke it. Also shut down the client of a gRPC hook removed from the configuration.

#### UI

#### Documentation
* Document how a release is named in the release guide: a few words that state its key features, used as the GitHub release title `x.y.z - <release name>`, which the guide's `gh release create` example now uses in place of the bare version.


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.2.0)

