Apache SkyWalking release guide
--------------------
If you're a committer, you can learn how to release SkyWalking in The Apache Way and start the voting process by reading this document.


## Prerequisites
Add your GPG public key into the [SkyWalking GPG KEYS](https://dist.apache.org/repos/dist/release/skywalking/KEYS) file.
- If you are a PMC member, use your Apache ID and password to log in this svn, and update the file. **Don't override the existing file.**
- If you are a committer, please ask a PMC member to help you. 

## Run the release script

The release script [`tools/releasing/release.sh`](https://github.com/apache/skywalking/blob/master/tools/releasing/release.sh) automates the following steps:

1. Verify GPG signer (`@apache.org` email required) and test GPG signing upfront
2. Check required tools (`gpg`, `svn`, `shasum`, `git`, `yq`, `gh`)
3. Detect current version from `pom.xml`
4. Calculate release version (strip `-SNAPSHOT`) and next version (bump minor)
5. Clone the repository, build source and binary tarballs, GPG sign, SHA512 checksum
6. Upload artifacts to SVN staging (`https://dist.apache.org/repos/dist/dev/skywalking/{version}`)
7. Generate the vote email (with sha512 checksums, submodule commit IDs, and release tag link filled in)
8. Prepare next version PR (bump version, rotate changelog, update `menu.yml`, push branch)

```bash
cd tools/releasing
bash release.sh
```

The script is interactive — it confirms the GPG signer and version numbers before proceeding.
The GPG signing is verified before the build starts, so passphrase or agent issues are caught early.
If the Maven build fails, the last 30 lines of the build log are printed for diagnosis.

After the script completes, copy the generated vote email and send it to `dev@skywalking.apache.org`.

## Call a vote in dev
The release script generates the vote email automatically. Send it to `dev@skywalking.apache.org`.

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


## Name the release
Each release has a name: a few plain words that state its key features, not a codename. Pick one to three
features, the ones the release's blog post or the top of its changelog leads with, and join them with commas
and "and":

- `10.3.0 - New Trace Model in BanyanDB`
- `11.0.0 - Horizon Ready, Runtime Rule Hot-Update and Live DSL Debugger`

Keep it short; a long list of features reads as a changelog, not a name. The [GitHub release](#publish-the-github-release)
is titled `x.y.z - <release name>`, and the name alone heads the release event on the website and is the first
highlight of the ANNOUNCE email.

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
3. Once `https://downloads.apache.org/skywalking/x.y.z/` lists the files, open a [website](https://github.com/apache/skywalking-website) PR that adds the release to `data/projects.yml` as latest, moves the previous release's links to the archive, and adds a release event headed with the [release name](#name-the-release), as the website's `CLAUDE.md` describes.
4. Send ANNOUNCE email to `dev@skywalking.apache.org`, `announce@apache.org`. The sender should use the Apache email account.
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

## Publish the GitHub release

Create a GitHub release for the signed tag, titled with the [release name](#name-the-release), so the
changelog is discoverable at `https://github.com/apache/skywalking/releases/tag/vx.y.z`:

```shell
gh release create vx.y.z --title "x.y.z - <release name>" --notes-file release-notes.md --verify-tag
```

After the `#### Download` and `#### Notice` sections of the previous release, add a `#### <release name>` section with screenshots of its features.

Use the version's section of `docs/en/changes/changes.md` as the release body, with **one formatting
step that matters**: GitHub renders release notes (like issues / PRs / comments) as GitHub-Flavored
Markdown, where a **single newline that continues a bullet's prose becomes a `<br>` line break** —
unlike the documentation website, which reflows soft-wrapped lines into a paragraph. So a bullet whose
prose is hard-wrapped across several source lines renders with jagged mid-sentence breaks. This is real,
not theoretical: on `v10.4.0` the wrapped `DrainBalancer` entry renders as
`… drain rebalancing (DrainBalancer).<br>Designed to replace DataCarrier …`.

Before pasting, **collapse each bullet's prose onto a single line** (join its wrapped continuation
lines). Nested sub-bullets (`  - …`) are **fine** — they are real list items and render as a clean nested
list (this is how `v10.4.0` structures its multi-part entries); only *prose* continuation lines turn into
`<br>`. Authoring `changes.md` entries this way in the first place — one line of prose per bullet, with
sub-bullets for structure — avoids this step entirely.

## Publish the Docker images

We have a [GitHub workflow](../../../.github/workflows/publish-docker.yaml) to automatically publish the Docker images to
Docker Hub once the GitHub release is published as a release (not a `pre-release`), all you need to do is to watch that workflow and see
whether it succeeds, if it fails, you can use the following steps to publish the Docker images in your local machine.

```shell
export SW_VERSION=x.y.z
git clone --depth 1 --branch v$SW_VERSION https://github.com/apache/skywalking.git
cd skywalking

svn co https://dist.apache.org/repos/dist/release/skywalking/$SW_VERSION release # (1)

export CONTEXT=release
export HUB=apache
export OAP_NAME=skywalking-oap-server
export TAG=$SW_VERSION
export DIST=<the binary package name inside (1), e.g. apache-skywalking-apm-8.8.0.tar.gz>
make docker.push
```

> Note: this repository no longer builds the UI Docker image. The official
> web UI ships from [`apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui),
> which has its own release cadence and publishes images independently:
> released images to Docker Hub `apache/skywalking-ui`, per-commit development
> images to `ghcr.io/apache/skywalking-horizon-ui`. The OAP release described
> above is independent of any Horizon UI release — they advance on
> separate schedules and are matched at deploy time, not at release time.

## Clean up the old releases
Once the latest release has been published, you should clean up the old releases from the mirror system.
1. Update the download links (source, dist, asc, and sha512) on the website to the archive repo (https://archive.apache.org/dist/skywalking).
2. Remove previous releases from https://dist.apache.org/repos/dist/release/skywalking/.

## Update the Quick Start Versions

We hosted the [SkyWalking Quick Start script](https://skywalking.apache.org/docs/main/latest/en/setup/backend/backend-docker/#start-the-storage-oap-and-booster-ui-with-docker-compose), 
which is a shell script that helps users to download and start SkyWalking quickly.
The versions of OAP, Horizon UI and BanyanDB are hard-coded in the script, so you need to update the versions in the script.

Update the versions for [shell script](https://github.com/apache/skywalking-website/blob/master/content/quickstart-docker.sh) and [powershell script](https://github.com/apache/skywalking-website/blob/master/content/quickstart-docker.ps1).
