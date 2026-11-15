{{/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "banyandb.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "banyandb.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := (include "banyandb.name" .) -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "banyandb.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "banyandb.labels" -}}
helm.sh/chart: {{ include "banyandb.chart" . }}
{{ include "banyandb.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "banyandb.selectorLabels" -}}
app.kubernetes.io/name: {{ include "banyandb.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "banyandb.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "banyandb.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Validate a generated Kubernetes name does not exceed a byte limit.
Usage: include "banyandb.validateNameLength" (dict "name" $name "limit" 63 "description" "..." "release" .Release.Name)
*/}}
{{- define "banyandb.validateNameLength" -}}
{{- $name := .name -}}
{{- $limit := .limit | default 63 -}}
{{- $description := .description -}}
{{- $release := .release -}}
{{- if gt (len $name) (int $limit) }}
{{- fail (printf "%s '%s' is %d bytes long, which exceeds the %d-byte Kubernetes limit. Shorten the Helm release name '%s' or set a shorter fullnameOverride." $description $name (len $name) $limit $release) }}
{{- end }}
{{- end }}

{{/*
Validate all generated resource names fit within Kubernetes limits.
StatefulSet names must leave room for the controller-revision-hash suffix
(<statefulset-name>-<10-char-hash>) so pod labels stay within 63 bytes.
*/}}
{{- define "banyandb.validateNames" -}}
{{- $fullname := include "banyandb.fullname" . -}}
{{- $release := .Release.Name -}}

{{/* fullname itself */}}
{{- include "banyandb.validateNameLength" (dict "name" $fullname "limit" 63 "description" "Generated fullname" "release" $release) }}

{{/* Standalone mode */}}
{{- if .Values.standalone.enabled }}
{{- include "banyandb.validateNameLength" (dict "name" $fullname "limit" 52 "description" "Standalone StatefulSet name" "release" $release) }}
{{- end }}

{{/* Cluster liaison */}}
{{- if and .Values.cluster.enabled .Values.cluster.liaison }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-liaison" $fullname) "limit" 52 "description" "Liaison StatefulSet name" "release" $release) }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-liaison-headless" $fullname) "limit" 63 "description" "Liaison headless service name" "release" $release) }}
{{- end }}

{{/* Cluster data roles */}}
{{- if and .Values.cluster.enabled .Values.cluster.data }}
{{- range $roleName, $roleConfig := .Values.cluster.data.roles }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-data-%s" $fullname $roleName) "limit" 52 "description" (printf "Data StatefulSet name for role '%s'" $roleName) "release" $release) }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-data-%s-headless" $fullname $roleName) "limit" 63 "description" (printf "Data headless service name for role '%s'" $roleName) "release" $release) }}
{{- end }}
{{- end }}

{{/* Auth Secret */}}
{{- if and .Values.auth.enabled (not .Values.auth.existingSecret) }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-auth" $fullname) "limit" 63 "description" "Auth Secret name" "release" $release) }}
{{- end }}

{{/* Standalone UI */}}
{{- if and .Values.cluster.enabled (eq .Values.cluster.ui.type "Standalone") }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-ui" $fullname) "limit" 63 "description" "UI Deployment name" "release" $release) }}
{{- end }}

{{/* FODC proxy */}}
{{- if and .Values.cluster.enabled .Values.cluster.fodc.enabled }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-fodc-proxy" $fullname) "limit" 63 "description" "FODC proxy Deployment name" "release" $release) }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-fodc-proxy-grpc" $fullname) "limit" 63 "description" "FODC proxy gRPC service name" "release" $release) }}
{{- include "banyandb.validateNameLength" (dict "name" (printf "%s-fodc-proxy-http" $fullname) "limit" 63 "description" "FODC proxy HTTP service name" "release" $release) }}
{{- end }}
{{- end }}

{{/*
Trace-pipeline plugin helpers
*/}}

{{/*
Return the data-node image to use when plugins are enabled.
This is the -plugins host image tag derived from the main image tag.
*/}}
{{- define "banyandb.pluginsHostImage" -}}
{{- $repo := .Values.image.repository -}}
{{- $tag := required "banyandb.image.tag is required when plugins are enabled" .Values.image.tag -}}
{{- printf "%s:%s-plugins" $repo $tag -}}
{{- end -}}

{{/*
Return the plugin carrier image reference.
The carrier tag defaults to <main-tag>-plugins-carrier to preserve lockstep parity.
*/}}
{{- define "banyandb.pluginsCarrierImage" -}}
{{- $plugins := .Values.plugins | default dict -}}
{{- $pluginsImage := $plugins.image | default dict -}}
{{- $repo := $pluginsImage.repository | default .Values.image.repository -}}
{{- $mainTag := required "banyandb.image.tag is required when plugins are enabled" .Values.image.tag -}}
{{- $tag := $pluginsImage.tag | default (printf "%s-plugins-carrier" $mainTag) -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}

{{/*
Return the third-party plugin image reference, if configured.
*/}}
{{- define "banyandb.pluginsThirdPartyImage" -}}
{{- $thirdParty := (default dict .Values.plugins).thirdParty | default dict -}}
{{- if and $thirdParty.repository $thirdParty.tag -}}
{{- printf "%s:%s" $thirdParty.repository $thirdParty.tag -}}
{{- end -}}
{{- end -}}

{{/*
Validate plugin configuration.
*/}}
{{- define "banyandb.validatePlugins" -}}
{{- $plugins := .Values.plugins | default dict -}}
{{- if $plugins.enabled }}
{{- if .Values.standalone.enabled }}
{{- fail "plugins.enabled cannot be used in standalone mode; plugins are supported on data nodes in cluster mode only" }}
{{- end }}
{{- if not .Values.cluster.enabled }}
{{- fail "plugins.enabled requires cluster.enabled=true; plugins are supported on data nodes in cluster mode only" }}
{{- end }}
{{- if not .Values.cluster.data }}
{{- fail "plugins.enabled requires cluster.data to be configured" }}
{{- end }}
{{- $mountMode := $plugins.mountMode | default "initContainer" }}
{{- if and (ne $mountMode "initContainer") (ne $mountMode "imageVolume") }}
{{- fail (printf "plugins.mountMode must be 'initContainer' or 'imageVolume', got '%s'" $mountMode) }}
{{- end }}
{{- if not .Values.image.tag }}
{{- fail "banyandb.image.tag is required when plugins.enabled=true" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
SchemaStoragePropertyServerEnv - injects property server env vars (data node only)
Includes: repair cron, schema server parameters, schema server TLS
*/}}
{{- define "banyandb.schemaStoragePropertyServerEnv" -}}
{{- $schemaMode := (default dict .Values.cluster.schemaStorage).mode | default "property" }}
{{- if ne $schemaMode "property" }}
{{- fail (printf "cluster.schemaStorage.mode must be 'property', got '%s'. etcd mode is no longer supported." $schemaMode) }}
{{- end }}
{{- if eq $schemaMode "property" }}
{{- $property := ((default dict .Values.cluster.schemaStorage).property) | default dict }}
{{- if $property.serverRepairCron }}
- name: BYDB_SCHEMA_PROPERTY_REPAIR_TRIGGER_CRON
  value: {{ $property.serverRepairCron | quote }}
{{- end }}
{{- $server := $property.server | default dict }}
{{- if $server.grpcHost }}
- name: BYDB_SCHEMA_SERVER_GRPC_HOST
  value: {{ $server.grpcHost | quote }}
{{- end }}
{{- if $server.grpcPort }}
- name: BYDB_SCHEMA_SERVER_GRPC_PORT
  value: {{ $server.grpcPort | quote }}
{{- end }}
{{- if $server.flushTimeout }}
- name: BYDB_SCHEMA_SERVER_FLUSH_TIMEOUT
  value: {{ $server.flushTimeout | quote }}
{{- end }}
{{- if $server.expireDeleteTimeout }}
- name: BYDB_SCHEMA_SERVER_EXPIRE_DELETE_TIMEOUT
  value: {{ $server.expireDeleteTimeout | quote }}
{{- end }}
{{- if $server.maxRecvMsgSize }}
- name: BYDB_SCHEMA_SERVER_MAX_RECV_MSG_SIZE
  value: {{ $server.maxRecvMsgSize | quote }}
{{- end }}
{{- if $server.repairTreeSlotCount }}
- name: BYDB_SCHEMA_SERVER_REPAIR_TREE_SLOT_COUNT
  value: {{ $server.repairTreeSlotCount | quote }}
{{- end }}
{{- if $server.repairBuildTreeCron }}
- name: BYDB_SCHEMA_SERVER_REPAIR_BUILD_TREE_CRON
  value: {{ $server.repairBuildTreeCron | quote }}
{{- end }}
{{- if $server.repairQuickBuildTreeTime }}
- name: BYDB_SCHEMA_SERVER_REPAIR_QUICK_BUILD_TREE_TIME
  value: {{ $server.repairQuickBuildTreeTime | quote }}
{{- end }}
{{- if $server.maxFileSnapshotNum }}
- name: BYDB_SCHEMA_SERVER_MAX_FILE_SNAPSHOT_NUM
  value: {{ $server.maxFileSnapshotNum | quote }}
{{- end }}
{{- if $server.minFileSnapshotAge }}
- name: BYDB_SCHEMA_SERVER_MIN_FILE_SNAPSHOT_AGE
  value: {{ $server.minFileSnapshotAge | quote }}
{{- end }}
{{- $serverTls := $server.tls | default dict }}
{{- if $serverTls.secretName }}
- name: BYDB_SCHEMA_SERVER_TLS
  value: "true"
- name: BYDB_SCHEMA_SERVER_CERT_FILE
  value: "/etc/tls/{{ $serverTls.secretName }}/tls.crt"
- name: BYDB_SCHEMA_SERVER_KEY_FILE
  value: "/etc/tls/{{ $serverTls.secretName }}/tls.key"
{{- end }}
{{- end }}
{{- end }}

{{/*
SchemaStoragePropertyClientEnv - injects property client env vars (data + liaison nodes)
Includes: sync interval, max recv msg size, client TLS
*/}}
{{- define "banyandb.schemaStoragePropertyClientEnv" -}}
{{- $schemaMode := (default dict .Values.cluster.schemaStorage).mode | default "property" }}
{{- if ne $schemaMode "property" }}
{{- fail (printf "cluster.schemaStorage.mode must be 'property', got '%s'. etcd mode is no longer supported." $schemaMode) }}
{{- end }}
{{- if eq $schemaMode "property" }}
{{- $property := ((default dict .Values.cluster.schemaStorage).property) | default dict }}
{{- if $property.clientSyncInterval }}
- name: BYDB_SCHEMA_PROPERTY_CLIENT_SYNC_INTERVAL
  value: {{ $property.clientSyncInterval | quote }}
{{- end }}
{{- if $property.clientMaxRecvMsgSize }}
- name: BYDB_SCHEMA_PROPERTY_CLIENT_MAX_RECV_MSG_SIZE
  value: {{ $property.clientMaxRecvMsgSize | quote }}
{{- end }}
{{- $clientTls := $property.tls | default dict }}
{{- if $clientTls.secretName }}
- name: BYDB_SCHEMA_PROPERTY_CLIENT_TLS
  value: "true"
- name: BYDB_SCHEMA_PROPERTY_CLIENT_CA_CERT
  value: "/etc/tls/{{ $clientTls.secretName }}/ca.crt"
{{- end }}
{{- end }}
{{- end }}


{{- define "banyandb.hasDataNodeListValue" -}}
{{- $dataNodeList := include "banyandb.dataNodeListValue" . }}
{{- if ne $dataNodeList "" }}true{{- end }}
{{- end }}

{{/*
Generate data node names list for "hot" role only
*/}}
{{- define "banyandb.dataNodeListValue" -}}
{{- $dataNodes := list }}
{{- $fullname := include "banyandb.fullname" . }}
{{- $namespace := .Release.Namespace }}
{{- range $roleName, $roleConfig := .Values.cluster.data.roles }}
  {{- if eq $roleName "hot" }}
    {{- $replicas := $roleConfig.replicas | default $.Values.cluster.data.nodeTemplate.replicas }}
    {{- range $i := until (int $replicas) }}
      {{- $podName := printf "%s-data-%s-%d" $fullname $roleName $i }}
      {{- $fqdn := printf "%s.%s-data-%s-headless.%s" $podName $fullname $roleName $namespace }}
      {{- $dataNodes = append $dataNodes (printf "%s:17912" $fqdn) }}
    {{- end }}
  {{- end }}
{{- end }}
{{- $dataNodes | join "," -}}
{{- end }}

{{/*
Generate DNS SRV address for a component
Format: _<port-name>._<proto>.<service-name>.<namespace>.svc.cluster.local
*/}}
{{- define "banyandb.dnsSrvAddress" -}}
{{- $component := .component }}
{{- $role := .role }}
{{- $fullname := include "banyandb.fullname" .root }}
{{- $namespace := .root.Release.Namespace }}
{{- $serviceName := "" }}
{{- $portName := "grpc" }}
{{- if eq $component "liaison" }}
  {{- $serviceName = printf "%s-liaison-headless" $fullname }}
  {{- $portName = "internal-grpc" }}
{{- else if eq $component "data" }}
  {{- $serviceName = printf "%s-data-%s-headless" $fullname $role }}
{{- end }}
{{- printf "_%s._tcp.%s.%s.svc.cluster.local" $portName $serviceName $namespace }}
{{- end }}

{{/*
Generate all DNS SRV addresses for node discovery
Returns comma-separated list of SRV addresses
*/}}
{{- define "banyandb.allDnsSrvAddresses" -}}
{{- $addresses := list }}
{{- /* Add liaison SRV address */}}
{{- $liaisonSrv := include "banyandb.dnsSrvAddress" (dict "root" . "component" "liaison") }}
{{- $addresses = append $addresses $liaisonSrv }}
{{- /* Add data node SRV addresses for each role */}}
{{- range $roleName, $roleConfig := .Values.cluster.data.roles }}
  {{- $dataSrv := include "banyandb.dnsSrvAddress" (dict "root" $ "component" "data" "role" $roleName) }}
  {{- $addresses = append $addresses $dataSrv }}
{{- end }}
{{- $addresses | join "," }}
{{- end }}

{{/*
Generate node discovery environment variables for a component
*/}}
{{- define "banyandb.nodeDiscoveryEnv" -}}
{{- $config := .root.Values.cluster.nodeDiscovery | default dict }}
{{- $mode := $config.mode | default "dns" }}
{{- if and (ne $mode "dns") (ne $mode "file") }}
{{- fail (printf "cluster.nodeDiscovery.mode must be 'dns' (default) or 'file', got '%s'. etcd mode is no longer supported; use 'dns' or 'file' instead." $mode) }}
{{- end }}

- name: BYDB_NODE_DISCOVERY_MODE
  value: {{ $mode | quote }}

{{- if eq $mode "dns" }}
{{- /* DNS mode configuration */}}
{{- /* Always auto-generate SRV addresses for all data nodes */}}
{{- $srvAddresses := include "banyandb.allDnsSrvAddresses" .root }}
{{- if $srvAddresses }}
- name: BYDB_NODE_DISCOVERY_DNS_SRV_ADDRESSES
  value: {{ $srvAddresses | quote }}
{{- end }}

{{- if $config.dns.fetchInitInterval }}
- name: BYDB_NODE_DISCOVERY_DNS_FETCH_INIT_INTERVAL
  value: {{ $config.dns.fetchInitInterval | quote }}
{{- end }}
{{- if $config.dns.fetchInitDuration }}
- name: BYDB_NODE_DISCOVERY_DNS_FETCH_INIT_DURATION
  value: {{ $config.dns.fetchInitDuration | quote }}
{{- end }}
{{- if $config.dns.fetchInterval }}
- name: BYDB_NODE_DISCOVERY_DNS_FETCH_INTERVAL
  value: {{ $config.dns.fetchInterval | quote }}
{{- end }}
{{- if $config.dns.grpcTimeout }}
- name: BYDB_NODE_DISCOVERY_GRPC_TIMEOUT
  value: {{ $config.dns.grpcTimeout | quote }}
{{- end }}

{{- /* Auto-generate TLS configuration based on existing gRPC TLS settings */}}
{{- $tlsEnabled := false }}
{{- $caCerts := list }}
{{- /* Check liaison TLS */}}
{{- if and .root.Values.cluster.liaison .root.Values.cluster.liaison.tls .root.Values.cluster.liaison.tls.grpcSecretName }}
  {{- $tlsEnabled = true }}
  {{- $caCerts = append $caCerts (printf "/etc/tls/%s/ca.crt" .root.Values.cluster.liaison.tls.grpcSecretName) }}
{{- end }}
{{- /* Check data nodes TLS for each role */}}
{{- range $roleName, $roleConfig := .root.Values.cluster.data.roles }}
  {{- if and $roleConfig.tls $roleConfig.tls.grpcSecretName }}
    {{- $tlsEnabled = true }}
    {{- $caCerts = append $caCerts (printf "/etc/tls/%s/ca.crt" $roleConfig.tls.grpcSecretName) }}
  {{- else if and $.root.Values.cluster.data.nodeTemplate.tls $.root.Values.cluster.data.nodeTemplate.tls.grpcSecretName }}
    {{- $tlsEnabled = true }}
    {{- $caCerts = append $caCerts (printf "/etc/tls/%s/ca.crt" $.root.Values.cluster.data.nodeTemplate.tls.grpcSecretName) }}
  {{- end }}
{{- end }}

{{- if $tlsEnabled }}
- name: BYDB_NODE_DISCOVERY_DNS_TLS
  value: "true"
{{- if $caCerts }}
- name: BYDB_NODE_DISCOVERY_DNS_CA_CERTS
  value: {{ $caCerts | join "," | quote }}
{{- end }}
{{- end }}
{{- else if eq $mode "file" }}
{{- $fileConfig := $config.file | default dict }}
- name: BYDB_NODE_DISCOVERY_FILE_PATH
  value: "/etc/banyandb/node-discovery/nodes.yaml"
{{- if $fileConfig.fetchInterval }}
- name: BYDB_NODE_DISCOVERY_FILE_FETCH_INTERVAL
  value: {{ $fileConfig.fetchInterval | quote }}
{{- end }}
{{- if $fileConfig.retryInitialInterval }}
- name: BYDB_NODE_DISCOVERY_FILE_RETRY_INITIAL_INTERVAL
  value: {{ $fileConfig.retryInitialInterval | quote }}
{{- end }}
{{- if $fileConfig.retryMaxInterval }}
- name: BYDB_NODE_DISCOVERY_FILE_RETRY_MAX_INTERVAL
  value: {{ $fileConfig.retryMaxInterval | quote }}
{{- end }}
{{- if $fileConfig.retryMultiplier }}
- name: BYDB_NODE_DISCOVERY_FILE_RETRY_MULTIPLIER
  value: {{ $fileConfig.retryMultiplier | quote }}
{{- end }}
{{- if $fileConfig.grpcTimeout }}
- name: BYDB_NODE_DISCOVERY_GRPC_TIMEOUT
  value: {{ $fileConfig.grpcTimeout | quote }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Resolve ConfigMap name for file-based node discovery
*/}}
{{- define "banyandb.nodeDiscoveryFileConfigMapName" -}}
{{- $root := .root | default . }}
{{- $config := $root.Values.cluster.nodeDiscovery | default dict }}
{{- $mode := $config.mode | default "dns" }}
{{- $file := $config.file | default dict }}
{{- $cm := $file.configMap | default dict }}
{{- if $cm.existingName }}
{{- $cm.existingName }}
{{- else if $cm.content }}
  {{- printf "%s-node-discovery" (include "banyandb.fullname" $root) }}
{{- else if eq $mode "file" }}
  {{- fail "cluster.nodeDiscovery.file.configMap.existingName or content must be set when cluster.nodeDiscovery.mode=file" }}
{{- else }}
{{- "" }}
{{- end }}
{{- end }}

{{/*
Resolve discovery file data key
*/}}
{{- define "banyandb.nodeDiscoveryFileKey" -}}
{{- $root := .root | default . }}
{{- $nodeDiscovery := $root.Values.cluster.nodeDiscovery | default dict }}
{{- $file := $nodeDiscovery.file | default dict }}
{{- $cm := $file.configMap | default dict }}
{{- default "nodes.yaml" $cm.key }}
{{- end }}

{{/*
Convert a human-readable size to an integer number of bytes.
Case-insensitive. All suffixes are 1024-based (binary): K/M/G/T, KB/MB/GB/TB,
Ki/Mi/Gi/Ti (also KiB/MiB/GiB/TiB). No suffix -> plain byte count. Empty or 0 -> 0.
*/}}
{{- define "banyandb.toBytes" -}}
{{- $s := . | toString | trim -}}
{{- if or (eq $s "") (eq $s "0") -}}
0
{{- else -}}
{{- if not (regexMatch "(?i)^[0-9]+(k|kb|ki|kib|m|mb|mi|mib|g|gb|gi|gib|t|tb|ti|tib)?$" $s) -}}
{{- fail (printf "banyandb.toBytes: invalid size %q; expected an integer optionally followed by K/M/G/T, KB/MB/GB/TB, or Ki/Mi/Gi/Ti (case-insensitive), e.g. 512Mi" $s) -}}
{{- end -}}
{{- $num := $s | regexFind "^[0-9]+" | int64 -}}
{{- $unit := lower (regexReplaceAll "^[0-9]+" $s "" | trim) -}}
{{- $mult := int64 1 -}}
{{- if or (eq $unit "k") (eq $unit "kb") (eq $unit "ki") (eq $unit "kib") -}}{{- $mult = int64 1024 -}}
{{- else if or (eq $unit "m") (eq $unit "mb") (eq $unit "mi") (eq $unit "mib") -}}{{- $mult = int64 1048576 -}}
{{- else if or (eq $unit "g") (eq $unit "gb") (eq $unit "gi") (eq $unit "gib") -}}{{- $mult = int64 1073741824 -}}
{{- else if or (eq $unit "t") (eq $unit "tb") (eq $unit "ti") (eq $unit "tib") -}}{{- $mult = int64 1099511627776 -}}
{{- end -}}
{{- mul $num $mult -}}
{{- end -}}
{{- end }}

{{/*
Canopy console image. Repository/pullPolicy default to the main image config;
the tag defaults to "<image.tag>-canopy" (lockstep, mirroring the plugins
carrier pattern) since the console ships in the same repo with a tag suffix.
*/}}
{{- define "banyandb.canopyImage" -}}
{{- $repo := .Values.canopy.image.repository | default .Values.image.repository -}}
{{- $tag := .Values.canopy.image.tag | default (printf "%s-canopy" (required "banyandb.image.tag is required" .Values.image.tag)) -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end }}

{{/*
Canopy image pullPolicy (defaults to the main image pullPolicy).
*/}}
{{- define "banyandb.canopyImagePullPolicy" -}}
{{- .Values.canopy.image.pullPolicy | default .Values.image.pullPolicy -}}
{{- end }}

{{/*
The BanyanDB HTTP service port canopy proxies to: liaison's in cluster mode,
standalone's otherwise.
*/}}
{{- define "banyandb.canopyHttpPort" -}}
{{- if .Values.cluster.enabled -}}
{{- .Values.cluster.liaison.httpSvc.port -}}
{{- else -}}
{{- .Values.standalone.httpSvc.port -}}
{{- end -}}
{{- end }}

{{/*
The URL scheme for BanyanDB HTTP: https when HTTP TLS is enabled for the
active mode (standalone.tls.httpSecretName / cluster.liaison.tls.httpSecretName).
*/}}
{{- define "banyandb.canopyScheme" -}}
{{- $scheme := "http" -}}
{{- if .Values.cluster.enabled -}}
{{- if (default dict .Values.cluster.liaison.tls).httpSecretName -}}{{- $scheme = "https" -}}{{- end -}}
{{- else -}}
{{- if (default dict .Values.standalone.tls).httpSecretName -}}{{- $scheme = "https" -}}{{- end -}}
{{- end -}}
{{- $scheme -}}
{{- end }}

{{/*
BANYANDB_TARGET for the canopy container. The <fullname>-http service exists
in both modes (standalone -> standalone pods, cluster -> liaison pods).
*/}}
{{- define "banyandb.canopyTarget" -}}
{{- printf "%s://%s-http:%v" (include "banyandb.canopyScheme" .) (include "banyandb.fullname" .) (include "banyandb.canopyHttpPort" .) -}}
{{- end }}

{{/*
MONITOR_TARGET for the canopy container (observability port on the same service).
*/}}
{{- define "banyandb.canopyMonitorTarget" -}}
{{- printf "%s://%s-http:2121" (include "banyandb.canopyScheme" .) (include "banyandb.fullname" .) -}}
{{- end }}

{{/*
The auth Secret name canopy uses: the user's existing one, or the chart-generated
<fullname>-canopy-auth.
*/}}
{{- define "banyandb.canopyAuthSecret" -}}
{{- if .Values.canopy.auth.existingSecret -}}
{{- .Values.canopy.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-canopy-auth" (include "banyandb.fullname" .) -}}
{{- end -}}
{{- end }}

{{/*
The auth config Secret name canopy uses in SSO mode: the user's existingAuthConfigSecret,
or the chart-generated <fullname>-canopy-auth-config.
*/}}
{{- define "banyandb.canopyAuthConfigSecret" -}}
{{- if .Values.canopy.auth.existingAuthConfigSecret -}}
{{- .Values.canopy.auth.existingAuthConfigSecret -}}
{{- else -}}
{{- printf "%s-canopy-auth-config" (include "banyandb.fullname" .) -}}
{{- end -}}
{{- end }}
