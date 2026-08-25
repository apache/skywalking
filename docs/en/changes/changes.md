## 11.1.0

#### Project

#### OAP Server
* Support hot-reloading the BanyanDB credentials from a new `secretsManagementFile` setting in `bydb.yml` (`SW_STORAGE_BANYANDB_SECRETS_MANAGEMENT_FILE`), matching the ElasticSearch storage plugin. The properties file carrying `user`/`password` is watched, so a rotation performed by a 3rd party tool such as Vault is applied without restarting the OAP. The credentials are read per RPC by the gRPC auth interceptor, so they are swapped without re-establishing the channel and without interrupting in-flight queries or writes; a file that sets only one of the two is rejected with an error log and the credentials in use are kept.
* Support hot-reloading the BanyanDB TLS trust CA. The file at `sslTrustCAPath` is now watched, and a change rebuilds the gRPC channel so the new CA takes effect without restarting the OAP. Previously the CA was only re-read after the certificate in use had already caused requests to fail, which meant a rotation was paid for with an outage. The replacement channel is created before the old one is released, so a failed rebuild leaves the current channel serving; requests already in flight finish on the old channel. Note the replacement re-picks an address from `targets`, so the OAP may connect to a different node after a rotation.
* Fix `MultipleFilesChangeMonitor` being able to silently disable every file watch in the OAP. Its registry of monitors was a plain `ArrayList` that `scanChanges()` iterated from the scheduler thread without holding the lock that `start()` / `stop()` take, so starting a monitor while a scan was in flight could raise a `ConcurrentModificationException` from the iterator. That exception escapes past the per-monitor catch, and an uncaught exception cancels a `scheduleAtFixedRate` task permanently — after which no secrets file, keystore, or TLS certificate is ever reloaded again, with nothing in the log to say so. The registry is now copy-on-write. The failure log in the same scan loop also now names the monitor that failed instead of printing an empty `gourp = `.
* Fix `MultipleFilesChangeMonitor` never honouring its watching period. `lastCheckTimestamp` was declared and compared against, but never assigned, so the guard always measured against `0` and passed — every registered monitor re-stat'd its watched files on each 200ms tick of the shared scheduler thread, and the `watchingPeriodInSec` constructor argument had no effect at all. This affects every file watch in the OAP: the ElasticSearch storage secrets / truststore / keystore watch, the BanyanDB credentials and trust CA watches, and the TLS certificate watches behind each OAP HTTP and gRPC server, all of which ask for 10 seconds. Change detection is now paced as configured, which also means it is no longer near-instant: a rotated file is picked up within the requested period rather than within ~200ms.

#### UI

#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.1.0)

