Apache SkyWalking release guide
--------------------
This documents guide every committer to release SkyWalking in Apache Way,
and also help committers to check the release for vote.


## SETUP YOUR DEVELOPMENT ENVIRONMENT
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

## TEST YOUR SETTINGS
```
mvn clean install -Papache-release (this will build artifacts, sources and sign)
```

## PREPARE THE RELEASE
```
mvn release:clean
mvn release:prepare
```

## STAGE THE RELEASE FOR A VOTE
```
mvn release:perform
```
The release will automatically be inserted into a temporary staging repository for you.

## Build the source code package
TODO

## Sign the distribution and source code package
TODO

## Upload to Apache svn
TODO


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

 * https://github.com/apache/incubator-skywalking/blob/master/CHANGELOG.md

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
Any PPMC, committer or contributor can test features and visualization this version, and feedback.
PPMC will decide whether start a vote.

## Call a vote in dev
Call a vote in `dev@skywalking.apache.org`

```
Mail title: [VOTE] Release Apache SkyWalking (incubating) version x.y.z

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking (Incubating) version x.y.z.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/master/CHANGELOG.md

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
```

## Vote Check
All PPMC members and committers should check these before vote +1.

1. Features test.
1. All artifacts in staging repository are published with .asc, .md5, *sha1 files
1. Source code and distribution package (apache-skywalking-incubating-x.y.z.src.tar.gz, apache-skywalking-incubating-x.y.z.tar.gz, apache-skywalking-incubating-x.y.z.zip)
`in svn.apache.org` with .asc, .sha512, .sha256
1. `LICENSE` and `NOTICE` are in Source code and distribution package.
1. Check `shasum`
1. Build distribution from source code package (apache-skywalking-incubating-x.y.z.src.tar.gz)
1. Apache RAT check.

