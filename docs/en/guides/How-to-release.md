Apache SkyWalking release guide
--------------------
If you're a committer, you can learn how to release SkyWalking in The Apache Way and start the voting process by reading this document.


## Prerequisites
Add your GPG public key into the [SkyWalking GPG KEYS](https://dist.apache.org/repos/dist/release/skywalking/KEYS) file.
- If you are a PMC member, use your Apache ID and password to log in this svn, and update the file. **Don't override the existing file.**
- If you are a committer, please ask a PMC member to help you. 

## Tag for the release

- Set the version number that you are about to release.

```bash
export RELEASE_VERSION=x.y.z # (example: RELEASE_VERSION=10.1.0)
export NEXT_RELEASE_VERSION=x.y.z # (example: NEXT_RELEASE_VERSION=10.2.0)
export PRODUCT_NAME="apache-skywalking-apm"
export SOURCE_FILE="dist/${PRODUCT_NAME}-${RELEASE_VERSION}.tar.gz"
export TARGET_DIR="tools/releasing"
```

- Create a new folder for the new release.

```bash
git clone https://github.com/apache/skywalking.git
cd skywalking
git checkout -b ${RELEASE_VERSION}-release # Create a branch for new release, such as 10.1.0-release
git submodule init
git submodule update
```

- Update the property `revision` in `pom.xml` file, and commit it.

```bash
./mvnw versions:set-property -DgenerateBackupPoms=false -Dproperty=revision -DnewVersion=${RELEASE_VERSION}
git add pom.xml
git commit -m "Prepare for release ${RELEASE_VERSION}"
```

- Tag the commit and push it to the upstream.

```bash
git tag v${RELEASE_VERSION}
git push origin v${RELEASE_VERSION}
```

## Build the binary package

```bash
./mvnw install package -DskipTests
mv "$SOURCE_FILE" "$TARGET_DIR/"
```

The release will be packaged first as `apache-skywalking-apm-x.y.z.tar.gz` in the `{PROJECT_ROOT}/dist` directory, and 
then moved to the `tools/releasing` directory.


## Build the source code package, sign the source code package and binary package
```bash
cd tools/releasing
bash create_release_tars.sh
```

This script takes care of the following things:
1. Use `v` + `RELEASE_VERSION` as tag to clone the codes.
1. Complete `git submodule init/update`.
1. Exclude all unnecessary files in the target source tar, such as `.git`, `.github`, and `.gitmodules`. See the script for more details.
1. Execute `gpg` and `shasum 512` for source tar and binary tar. 

`apache-skywalking-apm-x.y.z-src.tgz` and files ending with `.asc` and `.sha512` may be found in the `tools/releasing` folder.

## Start the next iteration

Once the binary and source packages are created, you can start updating the version to the next number and open a pull request.

```bash
# Update the version to the next snapshot version still in the same branch, such as 10.1.0-release
./mvnw versions:set-property -DgenerateBackupPoms=false -Dproperty=revision -DnewVersion=${NEXT_RELEASE_VERSION}-SNAPSHOT
git add pom.xml
git commit -m "Start next iteration ${NEXT_RELEASE_VERSION}"
```

Update the change log files for the next iteration.
* Rename [latest changes](../changes/changes.md) to `changes-{RELEASE_VERSION}.md`.
* Reset the [latest changes](../changes/changes.md) to the new version.
* Update Changelog in the `menu.yml` to link to `changes-{RELEASE_VERSION}.md`.

```bash
git push
gh pr create --fill # If you have gh cli installed and configured, or open the pull request in https://github.com/apache/skywalking/pulls
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
