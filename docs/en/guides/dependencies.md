# OAP backend dependency management
> This section is only applicable to dependencies of the OAP server and UI.

As one of the Top Level Projects of The Apache Software Foundation (ASF), SkyWalking must follow the [ASF 3RD PARTY LICENSE POLICY](https://apache.org/legal/resolved.html). So if you're adding new dependencies to the project, you should make sure that the new dependencies would not break the policy, and add their LICENSE and NOTICE to the project.

We use [license-eye](https://github.com/apache/skywalking-eyes) to help you make sure that you haven't missed out any new dependencies:
- Install `license-eye` according to [the doc](https://github.com/apache/skywalking-eyes#usage).
- Run `license-eye dependency resolve --summary ./dist-material/release-docs/LICENSE.tpl` in the root directory of this project.
- Check the modified lines in `./dist-material/release-docs/LICENSE` (via command `git diff -U0 ./dist-material/release-docs/LICENSE`) and
  check whether the new dependencies' licenses are compatible with Apache 2.0.
- Add the new dependencies' notice files (if any) to `./dist-material/release-docs/NOTICE` if they are Apache 2.0 license. Copy their license files to `./dist-material/release-docs/licenses` if they are not standard Apache 2.0 license.
- Copy the new dependencies' license file to `./dist-material/release-docs/licenses` if they are not standard Apache 2.0 license.
