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

package org.apache.skywalking.apm.agent.core.profile;

/**
 * Wrapper {@link ProfileStatus}, make sure {@link org.apache.skywalking.apm.agent.core.context.TracingContext} with {@link ThreadProfiler} have same reference with {@link ProfileStatus},
 * And only the profile module could change the status
 */
public class ProfileStatusReference {

    private volatile ProfileStatus status;

    private ProfileStatusReference(ProfileStatus status) {
        this.status = status;
    }

    /**
     * Create with not watching
     */
    public static ProfileStatusReference createWithNone() {
        return new ProfileStatusReference(ProfileStatus.NONE);
    }

    /**
     * Create with pending to profile
     */
    public static ProfileStatusReference createWithPending() {
        return new ProfileStatusReference(ProfileStatus.PENDING);
    }

    public ProfileStatus get() {
        return this.status;
    }

    /**
     * The profile monitoring is watching, wait for some profile conditions.
     */
    public boolean isBeingWatched() {
        return this.status != ProfileStatus.NONE;
    }

    public boolean isProfiling() {
        return this.status == ProfileStatus.PROFILING;
    }

    /**
     * Update status, only access with profile module
     */
    void updateStatus(ProfileStatus status) {
        this.status = status;
    }

}
