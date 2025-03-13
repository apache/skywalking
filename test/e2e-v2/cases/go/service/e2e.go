// Licensed to Apache Software Foundation (ASF) under one or more contributor
// license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright
// ownership. Apache Software Foundation (ASF) licenses this file to you under
// the Apache License, Version 2.0 (the "License"); you may
// not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package main

import (
	"github.com/apache/skywalking-go/toolkit/trace"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/gin-gonic/gin"

	_ "github.com/apache/skywalking-go"
)

func main() {
	upstream := os.Getenv("UPSTREAM_URL")
	engine := gin.New()
	engine.Handle("POST", "/correlation", func(context *gin.Context) {
		time.Sleep(time.Duration(500) * time.Millisecond)
		trace.SetCorrelation("MIDDLE_KEY", "go-service")
		res, err := http.Post(upstream, "text/html", nil)
		if err != nil {
			context.Status(http.StatusInternalServerError)
			log.Printf("request UPSTREAM_URL error: %v \n", err)
			return
		}
		_, err = http.Get("http://localhost:8080/ignored.html")
		if err != nil {
			context.Status(http.StatusInternalServerError)
			log.Printf("request ignored.html error: %v \n", err)
			return
		}
		defer res.Body.Close()
		body, err := ioutil.ReadAll(res.Body)
		if err != nil {
			context.Status(http.StatusInternalServerError)
			log.Printf("read http response error: %v \n", err)
			return
		}
		context.Status(res.StatusCode)
		context.Writer.Write(body)
	})

	engine.Handle("GET", "/ignored.html", func(context *gin.Context) {
		context.String(200, "Nobody cares me.")
	})

	_ = engine.Run(":8080")
}
