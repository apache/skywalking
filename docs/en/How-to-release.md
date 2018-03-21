Apache SkyWalking release guide
--------------------
This document guides every committer to release SkyWalking in Apache Way,
and also help committers to check the release for vote.


## Setup your development environment
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

## Test your settings
```
mvn clean install -Papache-release (this will build artifacts, sources and sign)
```

## Prepare the release
```
mvn release:clean
mvn release:prepare -DautoVersionSubmodules=true
```

## Stage the release for a vote
```
mvn release:perform -DskipTests -Ptravis-ci-submodule
```
The release will automatically be inserted into a temporary staging repository for you.

## Build and sign the source code package
```shell
switch to release version tag
cd tools/releasing
sh create_source_release.sh
```

The `apache-skywalking-apm-incubating-x.y.z-src.tgz` should be found in `tools/releasing` folder,
with .asc, .sha512, .md5

## Find and download distribution in Apache Nexus Staging repositories
1. Use ApacheId to login `https://repository.apache.org/`
1. Go to `https://repository.apache.org/#stagingRepositories`
1. Search `skywalking` and find your staging repository
1. Close the repository and wait for all checks pass.
1. Go to `{REPO_URL}/org/apache/skywalking/apache-skywalking-apm-incubating/x.y.z`
1. Download `.tar.gz` and `.zip` with .asc and .sha1


## Upload to Apache svn
1. Use ApacheId to login `https://dist.apache.org/repos/dist/dev/incubator/skywalking/`
1. Create folder, named by release version
1. Upload Source code and distribution package (apache-skywalking-incubating-x.y.z-src.tar.gz, apache-skywalking-incubating-x.y.z.tar.gz, apache-skywalking-incubating-x.y.z.zip) 
`in svn.apache.org` with .asc, .sha512

## Make the internal announcements
Send an announcement mail in dev mail list.

```
Mail title: [ANNOUNCE] SkyWalking x.y.z test build available

Mail content:
The test build of x.y.z is available.

This is our Apache Incubator release.
We welcome any comments you may have, and will take all feedback into
account if a quality vote is called for this build.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/master/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag)

Release CommitID :

 * (Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md

A vote regarding the quality of this test build will be initiated
within the next couple of days.
```

## Wait at least 48 hours for test responses
Any PPMC, committer or contributor can test features for releasing, and feedback.
Based on that, PPMC will decide whether start a vote.

## Call a vote in dev
Call a vote in `dev@skywalking.apache.org`

```
Mail title: [VOTE] Release Apache SkyWalking (incubating) version x.y.z

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking (Incubating) version x.y.z.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag)

Release CommitID :

 * (Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request all PPMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```

## Vote Check
All PPMC members and committers should check these before vote +1.

1. Features test.
1. All artifacts in staging repository are published with .asc, .md5, *sha1 files
1. Source code and distribution package (apache-skywalking-incubating-x.y.z-src.tar.gz, apache-skywalking-incubating-x.y.z.tar.gz, apache-skywalking-incubating-x.y.z.zip)
are in `https://dist.apache.org/repos/dist/dev/incubator/skywalking/x.y.z` with .asc, .sha512
1. `LICENSE` and `NOTICE` are in Source code and distribution package.
1. Check `shasum -c apache-skywalking-apm-incubating-x.y.z-src.tgz.sha512`
1. Build distribution from source code package (apache-skywalking-incubating-x.y.z-src.tar.gz) by following this [doc](https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md#build-from-apache-source-codes).
1. Apache RAT check. Run `mvn apache-rat:check`.

## Call for a vote in Apache IPMC
Call a vote in `general@incubator.apache.org`

```
Mail title: [VOTE] Release Apache SkyWalking (incubating) version x.y.z

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking (Incubating) version x.y.z.

Vote Thread:

 * From `list.apache.org`

Result Thread:

 * From the vote thread.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag)

Release CommitID :

 * (Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request all PPMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```
