{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "skywalking.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "skywalking.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create a default fully qualified oap name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "skywalking.oap.fullname" -}}
{{ template "skywalking.fullname" . }}-{{ .Values.oap.name }}
{{- end -}}

{{/*
Create a default fully qualified ui name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "skywalking.ui.fullname" -}}
{{ template "skywalking.fullname" . }}-{{ .Values.ui.name }}
{{- end -}}

{{/*
Create the name of the service account to use for the oap cluster
*/}}
{{- define "skywalking.serviceAccountName.oap" -}}
{{ default (include "skywalking.oap.fullname" .) .Values.serviceAccounts.oap }}
{{- end -}}

{{- define "call-nested" }}
{{- $dot := index . 0 }}
{{- $subchart := index . 1 }}
{{- $template := index . 2 }}
{{- include $template (dict "Chart" (dict "Name" $subchart) "Values" (index $dot.Values $subchart) "Release" $dot.Release "Capabilities" $dot.Capabilities) }}
{{- end }}

{{- define "skywalking.containers.wait-for-es" -}}
- name: wait-for-elasticsearch
  image: busybox:1.30
  imagePullPolicy: IfNotPresent
  command: ['sh', '-c', 'for i in $(seq 1 60); do nc -z -w3 {{ include "call-nested" (list . "elasticsearch" "elasticsearch.client.fullname") }} 9200 && exit 0 || sleep 5; done; exit 1']
{{- end -}}
