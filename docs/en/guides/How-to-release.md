Apache SkyWalking release guide
--------------------
If you're a committer, you can learn how to release SkyWalking in The Apache Way and start the voting process by reading this document.


## Prerequisites
Add your GPG public key into the [SkyWalking GPG KEYS](https://dist.apache.org/repos/dist/release/skywalking/KEYS) file.
- If you are a PMC member, use your Apache ID and password to log in this svn, and update the file. **Don't override the existing file.**
- If you are a committer, please ask a PMC member to help you. 

## Create artifacts for the release

- Create a new empty folder to do the release work.
- Set the version numbers and run the release script file [`tools/releasing/create_release_tars.sh`](https://github.com/apache/skywalking/blob/master/tools/releasing/create_release_tars.sh)

```bash
export RELEASE_VERSION=x.y.z # (example: RELEASE_VERSION=10.1.0)
export NEXT_RELEASE_VERSION=x.y.z # (example: NEXT_RELEASE_VERSION=10.2.0)
curl -Ls https://raw.githubusercontent.com/apache/skywalking/refs/heads/master/tools/releasing/create_release_tars.sh | bash -
```

After all the steps are completed, you will have the following files in the folder:

```text
apache-skywalking-apm-${RELEASE_VERSION}-bin.tar.gz
apache-skywalking-apm-${RELEASE_VERSION}-bin.tar.gz.asc
apache-skywalking-apm-${RELEASE_VERSION}-bin.tar.gz.sha512
apache-skywalking-apm-${RELEASE_VERSION}-src.tar.gz
apache-skywalking-apm-${RELEASE_VERSION}-src.tar.gz.asc
apache-skywalking-apm-${RELEASE_VERSION}-src.tar.gz.sha512
```

## Start the next iteration

Once the binary and source packages are created, you can start updating the version to the next number and open a pull request.

```bash
curl -Ls https://raw.githubusercontent.com/apache/skywalking/refs/heads/master/tools/releasing/start_next_version.sh | bash -
```

## Upload to Apache svn
1. Use your Apache ID to log in to `https://dist.apache.org/repos/dist/dev/skywalking/`.
1. Create a folder and name it by the release version and round, such as: `x.y.z`
1. Upload the source code package to the folder with files ending with `.asc` and `.sha512`.
    * Package name: `apache-skywalking-x.y.z-src.tar.gz`
    * See Section "Build and sign the source code package" for more details 
1. Upload the distribution package to the folder with files ending with `.asc` and `.sha512`.
    * Package name:  `apache-skywalking-bin-x.y.z.tar.gz`.
    * Create a `.sha512` package: `shasum -a 512 file > file.sha512`

## Call a vote in dev
Call a vote in `dev@skywalking.apache.org`

```
Mail title: [VOTE] Release Apache SkyWalking version x.y.z

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking version x.y.z.

Release notes:

 * https://github.com/apache/skywalking/blob/master/docs/en/changes/changes-x.y.z.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-bin-x.x.x.tar.gz

Release Tag :

 * (Git Tag) vx.y.z

Release CommitID :

 * https://github.com/apache/skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/skywalking-booster-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * https://dist.apache.org/repos/dist/release/skywalking/KEYS

Guide to build the release from source :

 * https://github.com/apache/skywalking/blob/vx.y.z/docs/en/guides/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request all PMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```

## Vote Check
All PMC members and committers should check these before casting +1 votes.

1. Features test.
1. Source code and distribution package (`apache-skywalking-x.y.z-src.tar.gz`, `apache-skywalking-bin-x.y.z.tar.gz`, `apache-skywalking-bin-x.y.z.zip`)
are found in `https://dist.apache.org/repos/dist/dev/skywalking/x.y.z` with `.asc` and `.sha512`.
1. `LICENSE` and `NOTICE` are in the source code and distribution package.
1. Check `shasum -c apache-skywalking-apm-x.y.z-src.tgz.sha512`.
1. Check `gpg --verify apache-skywalking-apm-x.y.z-src.tgz.asc apache-skywalking-apm-x.y.z-src.tgz`
1. Build a distribution package from the source code package (`apache-skywalking-x.y.z-src.tar.gz`) by following this [doc](https://github.com/apache/skywalking/blob/master/docs/en/guides/How-to-build.md#build-from-apache-source-code-release).
1. Check the Apache License Header. Run `docker run --rm -v $(pwd):/github/workspace apache/skywalking-eyes header check`. (No binaries in source codes)


The voting process is as follows:
1. All PMC member votes are +1 binding, and all other votes are +1 but non-binding.
1. If you obtain at least 3 (+1 binding) votes with more +1 than -1 votes within 72 hours, the release will be approved.


## Publish the release
1. Move source codes tar and distribution packages to `https://dist.apache.org/repos/dist/release/skywalking/`.
```
> export SVN_EDITOR=vim
> svn mv https://dist.apache.org/repos/dist/dev/skywalking/x.y.z https://dist.apache.org/repos/dist/release/skywalking
....
enter your apache password
....

```
2. Public download source and distribution tar/zip with asc and sha512 are located in `http://www.apache.org/dyn/closer.cgi/skywalking/x.y.z/xxx`.
The Apache mirror path is the only release information that we publish.
3. Update the website download page. http://skywalking.apache.org/downloads/ . Add a new download source, distribution, sha512, asc, and document
links. The links can be found following rules (3) to (6) above.
4. Add a release event on the website homepage and event page. Announce the public release with changelog or key features.
5. Send ANNOUNCE email to `dev@skywalking.apache.org`, `announce@apache.org`. The sender should use the Apache email account.
```
Mail title: [ANNOUNCE] Apache SkyWalking x.y.z released

Mail content:
Hi all,

Apache SkyWalking Team is glad to announce the first release of Apache SkyWalking x.y.z.

SkyWalking: APM (application performance monitor) tool for distributed systems,
especially designed for microservices, cloud native and container-based architectures.

This release contains a number of new features, bug fixes and improvements compared to
version a.b.c(last release). The notable changes since x.y.z include:

(Highlight key changes)
1. ...
2. ...
3. ...

Please refer to the change log for the complete list of changes:
https://skywalking.apache.org/docs/main/vx.y.z/en/changes/changes/

Apache SkyWalking website:
http://skywalking.apache.org/

Downloads:
https://skywalking.apache.org/downloads/#SkyWalkingAPM

Twitter:
https://twitter.com/ASFSkyWalking

SkyWalking Resources:
- GitHub: https://github.com/apache/skywalking
- Issue: https://github.com/apache/skywalking/issues
- Mailing list: dev@skywalkiing.apache.org


- Apache SkyWalking Team
```

## Publish the Docker images

We have a [GitHub workflow](../../../.github/workflows/publish-docker.yaml) to automatically publish the Docker images to
Docker Hub after you set the version from `pre-release` to `release`, all you need to do is to watch that workflow and see
whether it succeeds, if it fails, you can use the following steps to publish the Docker images in your local machine.

```shell
export SW_VERSION=x.y.z
git clone --depth 1 --branch v$SW_VERSION https://github.com/apache/skywalking.git
cd skywalking

svn co https://dist.apache.org/repos/dist/release/skywalking/$SW_VERSION release # (1)

export CONTEXT=release
export HUB=apache
export OAP_NAME=skywalking-oap-server
export UI_NAME=skywalking-ui
export TAG=$SW_VERSION
export DIST=<the binary package name inside (1), e.g. apache-skywalking-apm-8.8.0.tar.gz>
make docker.push
```

## Clean up the old releases
Once the latest release has been published, you should clean up the old releases from the mirror system.
1. Update the download links (source, dist, asc, and sha512) on the website to the archive repo (https://archive.apache.org/dist/skywalking).
2. Remove previous releases from https://dist.apache.org/repos/dist/release/skywalking/.

## Update the Quick Start Versions

We hosted the [SkyWalking Quick Start script](https://skywalking.apache.org/docs/main/latest/en/setup/backend/backend-docker/#start-the-storage-oap-and-booster-ui-with-docker-compose), 
which is a shell script that helps users to download and start SkyWalking quickly.
The versions of OAP and BanyanDB are hard-coded in the script, so you need to update the versions in the script.

Update the versions for [shell script](https://github.com/apache/skywalking-website/blob/master/content/quickstart-docker.sh#L23-L24) and [powershell script](https://github.com/apache/skywalking-website/blob/master/content/quickstart-docker.ps1#L18-L19).
