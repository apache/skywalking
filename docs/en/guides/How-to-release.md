Apache SkyWalking release guide
--------------------
This document guides every committer to release SkyWalking in Apache Way,
and also help committers to check the release for vote.


## Setup your development environment
Follow [Apache maven deployment environment document](http://www.apache.org/dev/publishing-maven-artifacts.html#dev-env)
to set gpg tool and encrypt passwords

Use the following block as a template and place it in ~/.m2/settings.xml

```
<settings>
...
  <servers>
    <!-- To publish a snapshot of some part of Maven -->
    <server>
      <id>apache.snapshots.https</id>
      <username> <!-- YOUR APACHE LDAP USERNAME --> </username>
      <password> <!-- YOUR APACHE LDAP PASSWORD (encrypted) --> </password>
    </server>
    <!-- To stage a release of some part of Maven -->
    <server>
      <id>apache.releases.https</id>
      <username> <!-- YOUR APACHE LDAP USERNAME --> </username>
      <password> <!-- YOUR APACHE LDAP PASSWORD (encrypted) --> </password>
    </server>
   ...
  </servers>
</settings>
```

## Add your GPG public key
1. Add your GPG public key into [SkyWalking GPG KEYS](https://dist.apache.org/repos/dist/release/skywalking/KEYS) file,
only if you are a committer, use your Apache id and password login this svn, and update file. **Don't override the existing file.**
1. Upload your GPG public key to public GPG site. Such as [MIT's site](http://pgp.mit.edu:11371/). This site should be in 
Apache maven staging repository check list.

## Test your settings
This step is only for test, if your env is set right, don't need to check every time.
```
./mvnw clean install -Pall (this will build artifacts, sources and sign)
```

## Prepare the release
```
./mvnw release:clean
./mvnw release:prepare -DautoVersionSubmodules=true -Pall
```

- Set version number as x.y.z, and tag as **v**x.y.z (version tag must start with **v**, you will find the purpose in next step.)

_You could do a GPG sign before doing release, if you need input the password to sign, and the maven don't give the chance,
but just failure. Run `gpg --sign xxx` to any file could remember the password for enough time to do release._ 

## Stage the release 
```
./mvnw release:perform -DskipTests -Pall
```

- The release will automatically be inserted into a temporary staging repository for you.

## Build and sign the source code package
```shell
export RELEASE_VERSION=x.y.z (example: RELEASE_VERSION=5.0.0-alpha)
cd tools/releasing
bash create_source_release.sh
```

This scripts should do following things
1. Use `v` + `RELEASE_VERSION` as tag to clone the codes.
1. Make `git submodule init/update` done.
1. Exclude all unnecessary files in the target source tar, such as .git, .github, .gitmodules. See the script for the details.
1. Do `gpg` and `shasum 512`. 


The `apache-skywalking-apm-x.y.z-src.tgz` should be found in `tools/releasing` folder,
with .asc, .sha512.

## Find and download distribution in Apache Nexus Staging repositories
1. Use ApacheId to login `https://repository.apache.org/`
1. Go to `https://repository.apache.org/#stagingRepositories`
1. Search `skywalking` and find your staging repository
1. Close the repository and wait for all checks pass. In this step, your GPG KEYS will be checked. See [set PGP document](#add-your-gpg-public-key),
if you haven't done it before.
1. Go to `{REPO_URL}/org/apache/skywalking/apache-skywalking-apm/x.y.z`
1. Download `.tar.gz` and `.zip` with .asc and .sha1


## Upload to Apache svn
1. Use ApacheId to login `https://dist.apache.org/repos/dist/dev/skywalking/`
1. Create folder, named by release version and round, such as: x.y.z
1. Upload Source code package to the folder with .asc, .sha512
    * Package name: apache-skywalking-x.y.z-src.tar.gz
    * See Section "Build and sign the source code package" for more details 
1. Upload distribution package to the folder with .asc, .sha512
    * Package name: apache-skywalking-bin-x.y.z.tar.gz, apache-skywalking-bin-x.y.z.zip
    * See Section "Find and download distribution in Apache Nexus Staging repositories" for more details
    * Create .sha512 package: `shasum -a 512 file > file.sha512`

## Make the internal announcements
Send an announcement mail in dev mail list.

```
Mail title: [ANNOUNCE] SkyWalking x.y.z test build available

Mail content:
The test build of x.y.z is available.

We welcome any comments you may have, and will take all feedback into
account if a quality vote is called for this build.

Release notes:

 * https://github.com/apache/skywalking/blob/master/changes/changes-x.y.z.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-bin-x.x.x.tar.gz
   - sha512xxxxyyyzzz apache-skywalking-apm-bin-x.x.x.zip

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag) x.y.z

Release CommitID :

 * https://github.com/apache/skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/skywalking-rocketbot-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * https://dist.apache.org/repos/dist/release/skywalking/KEYS

Guide to build the release from source :

 * https://github.com/apache/skywalking/blob/x.y.z/docs/en/guides/How-to-build.md

A vote regarding the quality of this test build will be initiated
within the next couple of days.
```

## Wait at least 48 hours for test responses
Any PMC, committer or contributor can test features for releasing, and feedback.
Based on that, PMC will decide whether start a vote.

## Call a vote in dev
Call a vote in `dev@skywalking.apache.org`

```
Mail title: [VOTE] Release Apache SkyWalking version x.y.z

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking version x.y.z.

Release notes:

 * https://github.com/apache/skywalking/blob/master/changes/changes-x.y.z.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-bin-x.x.x.tar.gz
   - sha512xxxxyyyzzz apache-skywalking-apm-bin-x.x.x.zip

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag) x.y.z

Release CommitID :

 * https://github.com/apache/skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/skywalking-rocketbot-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * https://dist.apache.org/repos/dist/release/skywalking/KEYS

Guide to build the release from source :

 * https://github.com/apache/skywalking/blob/x.y.z/docs/en/guides/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request all PMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```

## Vote Check
All PMC members and committers should check these before vote +1.

1. Features test.
1. All artifacts in staging repository are published with .asc, .md5, *sha1 files
1. Source code and distribution package (apache-skywalking-x.y.z-src.tar.gz, apache-skywalking-bin-x.y.z.tar.gz, apache-skywalking-bin-x.y.z.zip)
are in `https://dist.apache.org/repos/dist/dev/skywalking/x.y.z` with .asc, .sha512
1. `LICENSE` and `NOTICE` are in Source code and distribution package.
1. Check `shasum -c apache-skywalking-apm-x.y.z-src.tgz.sha512`
1. Check `gpg --verify apache-skywalking-apm-x.y.z-src.tgz.asc apache-skywalking-apm-x.y.z-src.tgz`
1. Build distribution from source code package (apache-skywalking-x.y.z-src.tar.gz) by following this [doc](https://github.com/apache/skywalking/blob/master/docs/en/guides/How-to-build.md#build-from-apache-source-code-release).
1. Apache RAT check. Run `./mvnw apache-rat:check`. (No binary in source codes)


Vote result should follow these.
1. PMC vote is +1 binding, all others is +1 no binding.
1. In 72 hours, you get at least 3 (+1 binding), and have more +1 than -1. Vote pass. 


## Publish release
1. Move source codes tar balls and distributions to `https://dist.apache.org/repos/dist/release/skywalking/`.
```
> export SVN_EDITOR=vim
> svn mv https://dist.apache.org/repos/dist/dev/skywalking/x.y.z https://dist.apache.org/repos/dist/release/skywalking
....
enter your apache password
....

```
2. Do release in nexus staging repo.
3. Public download source and distribution tar/zip locate in `http://www.apache.org/dyn/closer.cgi/skywalking/x.y.z/xxx`.
We only publish Apache mirror path as release info.
4. Public asc and sha512 locate in `https://www.apache.org/dist/skywalking/x.y.z/xxx`
5. Public KEYS pointing to  `https://www.apache.org/dist/skywalking/KEYS`
6. Update website download page. http://skywalking.apache.org/downloads/ . Include new download source, distribution, sha512, asc and document
links. Links could be found by following above rules(3)-(6).
7. Add a release event on website homepage and event page. Announce the public release with changelog or key features.
8. Send ANNOUNCE email to `dev@skywalking.apache.org`, `announce@apache.org`, the sender should use Apache email account.
```
Mail title: [ANNOUNCE] Apache SkyWalking x.y.z released

Mail content:
Hi all,

Apache SkyWalking Team is glad to announce the first release of Apache SkyWalking x.y.z.

SkyWalking: APM (application performance monitor) tool for distributed systems,
especially designed for microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

This release contains a number of new features, bug fixes and improvements compared to
version a.b.c(last release). The notable changes since x.y.z include:

(Highlight key changes)
1. ...
2. ...
3. ...

Please refer to the change log for the complete list of changes:
https://github.com/apache/skywalking/blob/master/changes/changes-x.y.z.md

Apache SkyWalking website:
http://skywalking.apache.org/

Downloads:
http://skywalking.apache.org/downloads/

Twitter:
https://twitter.com/ASFSkyWalking

SkyWalking Resources:
- GitHub: https://github.com/apache/skywalking
- Issue: https://github.com/apache/skywalking/issues
- Mailing list: dev@skywalkiing.apache.org


- Apache SkyWalking Team
```
