/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.atlas.web.security;

import org.apache.atlas.web.filters.HeadersUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;

public class CustomAuthenticationEntrypoint extends DelegatingAuthenticationEntryPoint {

    public CustomAuthenticationEntrypoint(LinkedHashMap< RequestMatcher, AuthenticationEntryPoint> entryPoints) {
        super(entryPoints);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        System.out.println("......---------");

        System.out.println(request);
        System.out.println(response);
        System.out.println(authException);

        String ajaxRequestHeader = request.getHeader(HeadersUtil.X_REQUESTED_WITH_KEY);
        response.setHeader(HeadersUtil.X_FRAME_OPTIONS_KEY, HeadersUtil.X_FRAME_OPTIONS_VAL);

        if (ajaxRequestHeader != null
                && HeadersUtil.X_REQUESTED_WITH_VALUE.equalsIgnoreCase(ajaxRequestHeader)) {
            response.sendError(HeadersUtil.SC_AUTHENTICATION_TIMEOUT, "session timed out");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    authException.getMessage());
        }
    }
}
