---
name: package
description: Rebuild the SkyWalking distribution and OAP Docker image after source changes. Use before running e2e tests so the image reflects your code changes. Avoids the "image looks updated but runtime has stale jars" trap.
argument-hint: "[oap|all|dist-only]"
---

# Package OAP Distribution & Docker Image

Rebuilding the Docker image after a source-code change has a **two-step dependency**: rebuild the dist tarball, then rebuild the image. Skipping the first step silently produces an image with stale jars — the Docker build still "succeeds," the image has a fresh timestamp, but the embedded jar is the one from your previous build.

## Why this matters

`make docker.oap` in the root `Makefile` is defined as:

```
docker.% push.docker.%: $(CONTEXT)/$(DIST) $(SW_ROOT)/docker/%/*
    $(DOCKER_RULE)
```

It depends on `$(CONTEXT)/$(DIST)` (the dist tarball) **as a file prerequisite**. If that tarball already exists, make does not regenerate it — it just copies whatever is on disk into `dist/docker_build/oap/` and runs `docker buildx build`. There is **no** dependency that rebuilds the tarball from source.

Only `make docker` triggers the full chain (`init → build.all → docker.all`). So:

| Command | Rebuilds source? | Rebuilds tarball? | Rebuilds image? |
|---------|:--:|:--:|:--:|
| `./mvnw -pl <module> package` | only that module | no | no |
| `./mvnw -pl apm-dist -am package -Pbackend,dist` | all backend deps + dist | yes | no |
| `make docker.oap` | **no** | **no** (uses whatever's on disk) | yes |
| `make docker` | yes (`build.all`) | yes | yes (`docker.all`) |

## `flatten:flatten` — always run it before `package`/`install`

SkyWalking's poms use `${revision}` as a placeholder version (e.g., `10.5.0-SNAPSHOT`). The `flatten-maven-plugin` resolves `${revision}` into concrete versions and writes a `.flattened-pom.xml`. Without it:

- Installed artifacts carry the literal string `${revision}` in their coordinates and cannot be resolved as dependencies.
- Downstream modules (including `apm-dist`) see an inconsistent dependency graph.
- Symptoms are subtle: `-pl <module> -am` may succeed in isolation but fail or pull in stale transitive artifacts when the same module is consumed by another build invocation in the same session.

**Always run `flatten:flatten` in the same goal chain as `package` or `install`.** The CI `dist-tar` job and the `compile` skill both do this. Example:

```bash
./mvnw clean flatten:flatten package -Pbackend,dist -DskipTests
```

Not optional — treat it as part of `package`.

## Pick the right command

### Changed OAP source → want a new image

```bash
# Recommended: full chain
make docker
```

or, faster and equivalent for backend-only changes (skips UI):

```bash
./mvnw -pl apm-dist -am -o clean flatten:flatten package -Pbackend,dist -DskipTests -Dcheckstyle.skip=true -Dmaven.javadoc.skip=true
make docker.oap
```

**Do not** just run `make docker.oap` after a code edit — the image will look rebuilt but your change will not be in the jars.
**Do not** skip `flatten:flatten` — see the section above.

### Changed only the dist packaging (e.g., `apm-dist/`, log4j2.xml)

```bash
./mvnw -pl apm-dist -am -o clean flatten:flatten package -Pbackend,dist -DskipTests
make docker.oap
```

### Changed only Dockerfile / entrypoint (`docker/oap/*`)

The tarball is untouched, so `make docker.oap` alone is correct:

```bash
make docker.oap
```

## Verify the fix reached the image

Docker's "image created" timestamp updates even when the content is stale (because `--no-cache` is used). **Don't trust the timestamp.** Verify the jar contents directly:

```bash
# Copy the jar out of the container
docker cp <container-name>:/skywalking/oap-libs/<module>-<version>.jar /tmp/verify.jar

# Extract the specific class
cd /tmp && jar -xf verify.jar <path/to/YourClass.class>

# Grep for a string your fix introduced (unique literal, method name, etc.)
grep -oa "myFixSignature" <path/to/YourClass.class>
```

If grep finds nothing, the image does not contain your fix — you forgot to rebuild the dist. Re-run with the correct chain.

## Common pitfalls

- **`make docker.oap` after `./mvnw package`**: The module jar is fresh in `oap-server/.../target/`, but the dist tarball at `dist/apache-skywalking-apm-bin.tar.gz` is untouched. Image uses the old jar.
- **Cancelling a `make docker` mid-flight**: leaves the dist half-rebuilt. Re-run `make docker` or delete `dist/` first.
- **Stale buildx cache**: not usually the issue (the Makefile passes `--no-cache`), but if you suspect it, run `docker buildx prune`.
- **Trusting image timestamps**: `docker images` shows when the image was *tagged*, not when its content actually changed. A no-op rebuild still updates the timestamp.
- **Interactive buildx hangs**: if `docker buildx build` sits silent for minutes with no stdout, kill it and retry. The buildx container driver occasionally stalls; a restart is faster than waiting.

## The golden rule

After any Java source edit that affects code shipped in `oap-libs/`, run one of:

1. `make docker` (full, safest)
2. `./mvnw -pl apm-dist -am -o package -Pbackend,dist -DskipTests -Dcheckstyle.skip=true && make docker.oap` (fast)

Then **verify the fix is in the image's jar** before running e2e. Don't assume.
