/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

pipeline {
    agent none

    options {
        buildDiscarder(logRotator(
            numToKeepStr: '60',
        ))
        timestamps()
        skipStagesAfterUnstable()
    }

    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx3g'
    }

    stages {
        stage('Install & Test') {
            parallel {
                stage('JDK 1.8 on Linux') {
                    agent {
                        label 'xenial'
                    }

                    tools {
                        jdk 'JDK 1.8 (latest)'
                    }

                    stages {
                        stage('SCM Checkout') {
                            steps {
                                deleteDir()
                                checkout scm
                                sh 'git submodule update --init'
                            }
                        }

                        stage('Check environment') {
                            steps {
                                sh 'env'
                                sh 'pwd'
                                sh 'ls'
                                sh 'git status'
                            }
                        }

                        stage('Test & Report') {
                            steps {
                                sh './mvnw -P"agent,backend,ui,dist,CI-with-IT" org.jacoco:jacoco-maven-plugin:0.8.3:prepare-agent clean install org.jacoco:jacoco-maven-plugin:0.8.3:report'
                                sh './mvnw javadoc:javadoc -Dmaven.test.skip=true'
                            }
                        }

                        stage('Check Dependencies Licenses') {
                            steps {
                                sh 'tar -zxf dist/apache-skywalking-apm-bin.tar.gz -C dist'
                                sh 'tools/dependencies/check-LICENSE.sh'
                            }
                        }
                    }

                    post {
                        success {
                            junit '**/target/surefire-reports/*.xml'
                        }

                        cleanup {
                            deleteDir()
                        }
                    }
                }
            }
        }
    }
}
