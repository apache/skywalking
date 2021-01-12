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
 */

package org.apache.skywalking.apm.webapp;

import java.io.IOException;
import org.apache.skywalking.apm.webapp.proxy.NotFoundHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {
    NotFoundHandler.class,
    ClassPathResource.class
})
public class NotFoundHandlerTest {
    @Mock
    private NotFoundHandler notFoundHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldInternalErrorWhenIndexPageIsMissing() throws Exception {
        ClassPathResource mockIndexResource = mock(ClassPathResource.class);
        when(mockIndexResource.getInputStream()).thenThrow(new IOException());

        PowerMockito.whenNew(ClassPathResource.class).withArguments("/public/index.html").thenReturn(mockIndexResource);

        when(notFoundHandler.renderDefaultPage()).thenCallRealMethod();
        ResponseEntity<String> response = notFoundHandler.renderDefaultPage();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
