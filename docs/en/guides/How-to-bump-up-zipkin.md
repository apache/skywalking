# How to bump up Zipkin Lens dependency

Because SkyWalking embeds Zipkin Lens UI as a part of the SkyWalking UI, and Zipkin Lens UI contains
a lot of other front-end dependencies that we also distribute in SkyWalking binary tars, so we have
to take care of the dependencies' licenses when we bump up the Zipkin Lens dependency.

Make sure to do the following steps when you bump up the Zipkin Lens dependency:

- Clone the Zipkin project into a directory.

```shell
ZIPKIN_VERSION=<the Zipkin version you want to bump to>
git clone https://github.com/openzipkin/zipkin && cd zipkin
git checkout $ZIPKIN_VERSION

cd zipkin-lens
```

- Create `.licenserc.yaml` with the following content.

```shell
cat > .licenserc.yaml << EOF
header:
  license:
    spdx-id: Apache-2.0
    copyright-owner: Apache Software Foundation
dependency:
  files:
    - package.json
  licenses:
    - name: cli-table
      version: 0.3.1
      license: MIT
    - name:  domutils
      version: 1.5.1
      license: BSD-2-Clause
    - name: rework
      version: 1.0.1
      license: MIT
EOF
```

- Create license template `LICENSE.tpl` with the following content.

```
{{ range .Groups }}
========================================================================
{{ .LicenseID }} licenses
========================================================================
The following components are provided under the {{ .LicenseID }} License. See project link for details.
{{- if eq .LicenseID "Apache-2.0" }}
The text of each license is the standard Apache 2.0 license.
{{- else }}
The text of each license is also included in licenses/LICENSE-[project].txt.
{{ end }}

    {{- range .Deps }}
    https://npmjs.com/package/{{ .Name }}/v/{{ .Version }} {{ .Version }} {{ .LicenseID }}
    {{- end }}
{{ end }}
```

- Make sure you're using the supported NodeJS version and NPM version.

```shell
node -v
# should be v14.x.x
npm -v
# should be 6.x.x
```

- Run the following command to generate the license file.

```shell
license-eye dependency resolve --summary LICENSE.tpl
```

- Copy the generated file `LICENSE` to replace the `zipkin-LICENSE` in SkyWalking repo.

Note: if there are dependencies that license-eye failed to identify the license, you should manually
identify the license and add it to the step above in `.licenserc.yaml`.
