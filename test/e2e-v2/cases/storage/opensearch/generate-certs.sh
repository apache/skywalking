#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

CERTS_DIR="./certs"
mkdir -p "$CERTS_DIR"

# Generate Root CA
openssl genrsa -out "$CERTS_DIR/root-ca-key.pem" 2048
openssl req -new -x509 -sha256 -key "$CERTS_DIR/root-ca-key.pem" \
  -subj "/C=US/ST=CA/L=Test/O=SkyWalking/OU=Test/CN=SkyWalking Root CA" \
  -out "$CERTS_DIR/root-ca.pem" -days 730

# Generate Node Certificate
openssl genrsa -out "$CERTS_DIR/node-key.pem" 2048
openssl req -new -key "$CERTS_DIR/node-key.pem" \
  -subj "/C=US/ST=CA/L=Test/O=SkyWalking/OU=Test/CN=opensearch" \
  -out "$CERTS_DIR/node.csr"

# Create SAN config for node cert
cat >"$CERTS_DIR/node-san.cnf" <<EOFSAN
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req

[req_distinguished_name]

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = opensearch
DNS.2 = localhost
IP.1 = 127.0.0.1
EOFSAN

openssl x509 -req -in "$CERTS_DIR/node.csr" \
  -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca-key.pem" -CAcreateserial \
  -out "$CERTS_DIR/node.pem" -days 730 -sha256 \
  -extfile "$CERTS_DIR/node-san.cnf" -extensions v3_req

# Generate Admin Certificate (for securityadmin tool)
openssl genrsa -out "$CERTS_DIR/admin-key.pem" 2048
openssl req -new -key "$CERTS_DIR/admin-key.pem" \
  -subj "/C=US/ST=CA/L=Test/O=SkyWalking/OU=Test/CN=admin" \
  -out "$CERTS_DIR/admin.csr"
openssl x509 -req -in "$CERTS_DIR/admin.csr" \
  -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca-key.pem" -CAcreateserial \
  -out "$CERTS_DIR/admin.pem" -days 730 -sha256

# Generate Client Certificate (for SkyWalking OAP)
# CN must match the username in roles_mapping (node-0.example.com)
openssl genrsa -out "$CERTS_DIR/client-key.pem" 2048
openssl req -new -key "$CERTS_DIR/client-key.pem" \
  -subj "/C=US/ST=CA/L=Test/O=SkyWalking/OU=Test/CN=node-0.example.com" \
  -out "$CERTS_DIR/client.csr"
openssl x509 -req -in "$CERTS_DIR/client.csr" \
  -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca-key.pem" -CAcreateserial \
  -out "$CERTS_DIR/client.pem" -days 730 -sha256

# Create PKCS12 keystore for client (for Java applications)
openssl pkcs12 -export -in "$CERTS_DIR/client.pem" -inkey "$CERTS_DIR/client-key.pem" \
  -out "$CERTS_DIR/client.p12" -name "node-0.example.com" -passout pass:changeit

# Create JKS truststore with root CA (remove existing if present)
rm -f "$CERTS_DIR/truststore.jks"
keytool -import -file "$CERTS_DIR/root-ca.pem" -alias root-ca \
  -keystore "$CERTS_DIR/truststore.jks" -storepass changeit -noprompt

# Clean up CSR and temp files
rm -f "$CERTS_DIR"/*.csr "$CERTS_DIR"/*.srl "$CERTS_DIR/node-san.cnf"

echo "✓ Certificates generated successfully in $CERTS_DIR"
ls -lh "$CERTS_DIR"
