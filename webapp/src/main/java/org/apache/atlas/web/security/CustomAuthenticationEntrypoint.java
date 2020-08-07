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
