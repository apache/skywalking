{{ .LicenseContent }}

=======================================================================
Apache SkyWalking Subcomponents:

The Apache SkyWalking project contains subcomponents with separate copyright
notices and license terms. Your use of the source code for the these
subcomponents is subject to the terms and conditions of the following
licenses.
========================================================================

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
      {{- $groupArtifact := regexSplit ":" .Name -1 }}
      {{- if eq (len $groupArtifact) 2 }}
        {{- $group := index $groupArtifact 0 }}
        {{- $artifact := index $groupArtifact 1 }}
    https://mvnrepository.com/artifact/{{ $group }}/{{ $artifact }}/{{ .Version }} {{ .LicenseID }}
      {{- else }}
    https://npmjs.com/package/{{ .Name }}/v/{{ .Version }} {{ .Version }} {{ .LicenseID }}
      {{- end }}
    {{- end }}
{{ end }}
=======================================================================
The zipkin-lens.jar dependency has more front-end dependencies in it and the front-end dependencies' licenses
are listed in zipkin-LICENSE.
