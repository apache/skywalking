## 11.2.0

#### Project
* Fix the release script on macOS: the source tarball carried an AppleDouble `._*` entry and `com.apple.provenance` xattr headers for every file (the 11.0.0 source release has them, and GNU tar extracts them as stray `._*` files with a warning per entry), the next-version PR step aborted because the single-branch release clone has no remote-tracking ref for the pushed branch, and the vote email left out the BanyanDB client proto submodule.

#### OAP Server

#### UI

#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.2.0)

