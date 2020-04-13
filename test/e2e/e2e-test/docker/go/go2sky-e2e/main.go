/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main

import (
	"flag"
	"io/ioutil"
	"log"
	"net/http"

	"github.com/SkyAPM/go2sky"
	httpPlugin "github.com/SkyAPM/go2sky/plugins/http"
	"github.com/SkyAPM/go2sky/reporter"
)

var (
	grpc        bool
	oapServer   string
	upstreamUrl string
	listenAddr  string
	serviceName string

	client *http.Client
)

func init() {
	flag.BoolVar(&grpc, "grpc", false, "use grpc reporter")
	flag.StringVar(&oapServer, "oap-server", "127.0.0.1:11800", "oap server address")
	flag.StringVar(&upstreamUrl, "upstream-url", "http://www.baidu.com", "upstream service url")
	flag.StringVar(&listenAddr, "listen-addr", ":8080", "listen address")
	flag.StringVar(&serviceName, "service-name", "go2sky", "service name")
}

func ServerHTTP(writer http.ResponseWriter, request *http.Request) {
	clientReq, err := http.NewRequest(http.MethodPost, upstreamUrl, nil)
	if err != nil {
		writer.WriteHeader(http.StatusInternalServerError)
		log.Printf("unable to create http request error: %v \n", err)
		return
	}
	clientReq = clientReq.WithContext(request.Context())
	res, err := client.Do(clientReq)
	if err != nil {
		writer.WriteHeader(http.StatusInternalServerError)
		log.Printf("unable to do http request error: %v \n", err)
		return
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		writer.WriteHeader(http.StatusInternalServerError)
		log.Printf("read http response error: %v \n", err)
		return
	}
	writer.WriteHeader(res.StatusCode)
	_, _ = writer.Write(body)
}

func main() {
	flag.Parse()

	var report go2sky.Reporter
	var err error
	if grpc {
		report, err = reporter.NewGRPCReporter(oapServer)
		if err != nil {
			log.Fatalf("crate grpc reporter error: %v \n", err)
		}
	} else {
		report, err = reporter.NewLogReporter()
		if err != nil {
			log.Fatalf("crate log reporter error: %v \n", err)
		}
	}

	tracer, err := go2sky.NewTracer(serviceName, go2sky.WithReporter(report))
	if err != nil {
		log.Fatalf("crate tracer error: %v \n", err)
	}

	client, err = httpPlugin.NewClient(tracer)
	if err != nil {
		log.Fatalf("create client error %v \n", err)
	}

	route := http.NewServeMux()
	route.HandleFunc("/", ServerHTTP)

	sm, err := httpPlugin.NewServerMiddleware(tracer)
	if err != nil {
		log.Fatalf("create client error %v \n", err)
	}
	err = http.ListenAndServe(listenAddr, sm(route))
	if err != nil {
		log.Fatal(err)
	}
}
