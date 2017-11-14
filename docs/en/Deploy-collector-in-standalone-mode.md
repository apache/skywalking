# Usage scenario
Standalong mode collector means don't support cluster. It uses H2 as storage layer implementation, suggest that use only for preview, test, demonstration, low throughputs and small scale system.

# Requirements
* JDK 8+

# Download
* [Releases](https://github.com/OpenSkywalking/skywalking/releases)

# Quick start
You can simplely tar/unzip and startup if ports 10800, 11800, 12800 are free.

- `tar -xvf skywalking-collector.tar.gz` in Linux, or unzip in windows.
- run `bin/startup.sh` or `bin/startup.bat`

You should keep the `config/application.yml` as default.

# Use Elastic Search instead of H2 as storage layer implementation
Even in standalone mode, collector can run with Elastic Search as storage. If so, uncomment the `storage` section in `application.yml`, set the config right.

