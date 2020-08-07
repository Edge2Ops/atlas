package org.apache.atlas.web.security;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.keycloak.adapters.springsecurity.token.AdapterTokenStoreFactory;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.adapters.springsecurity.token.SpringSecurityAdapterTokenStoreFactory;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.Assert;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logs the current user out of Keycloak.
 *
 * @author <a href="mailto:srossillo@smartling.com">Scott Rossillo</a>
 * @version $Revision: 1 $
 */
public class CustomKeycloakLogoutHandler implements LogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(org.keycloak.adapters.springsecurity.authentication.KeycloakLogoutHandler.class);

    private AdapterDeploymentContext adapterDeploymentContext;
    private AdapterTokenStoreFactory adapterTokenStoreFactory = new SpringSecurityAdapterTokenStoreFactory();

    public CustomKeycloakLogoutHandler(AdapterDeploymentContext adapterDeploymentContext) {
        Assert.notNull(adapterDeploymentContext);
        this.adapterDeploymentContext = adapterDeploymentContext;
    }

    public void setAdapterTokenStoreFactory(AdapterTokenStoreFactory adapterTokenStoreFactory) {
        this.adapterTokenStoreFactory = adapterTokenStoreFactory;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            log.warn("Cannot log out without authentication");
            return;
        }
        else if (!KeycloakAuthenticationToken.class.isAssignableFrom(authentication.getClass())) {
            log.warn("Cannot log out a non-Keycloak authentication: {}", authentication);
            return;
        }

        try {
            handleSingleSignOut(request, response, (KeycloakAuthenticationToken) authentication);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    protected void handleSingleSignOut(HttpServletRequest request, HttpServletResponse response, KeycloakAuthenticationToken authenticationToken) throws UnsupportedEncodingException {
        HttpFacade facade = new SimpleHttpFacade(request, response);
        AdapterDeploymentContext ctx = new AdapterDeploymentContext();
        KeycloakDeployment deploymentOriginal = adapterDeploymentContext.resolveDeployment(facade);
        KeycloakDeployment deployment = new KeycloakDeployment();

        System.out.println("Saved Realm: " + deploymentOriginal.getRealm());

        AdapterConfig cfg = new AdapterConfig();
        cfg.setAuthServerUrl(deploymentOriginal.getAuthServerBaseUrl());
        cfg.setSslRequired(deploymentOriginal.getSslRequired().toString());
        cfg.setResource(deploymentOriginal.getResourceName());
        cfg.setPublicClient(false);
        cfg.setConfidentialPort(deploymentOriginal.getConfidentialPort());
        cfg.setPrincipalAttribute(deploymentOriginal.getPrincipalAttribute());
        cfg.setAutodetectBearerOnly(true);
        cfg.setCredentials(deploymentOriginal.getResourceCredentials());
        cfg.setRealm(deploymentOriginal.getRealm());

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                System.out.println("cookie: " + c.getName() + "=" + c.getValue() + "   condition: " + c.getName() + "atlan_tenant");
                if (c.getName().equalsIgnoreCase("atlan_tenant")) {
                    cfg.setRealm(c.getValue());
                }
            }
        }

        if (request.getQueryString() != null) {
            Map<String, String> queryParams = convertQueryStringToMap(request.getQueryString());
            String tenantId = queryParams.get("tenant");
            if (tenantId != null && !tenantId.isEmpty()) {
                cfg.setRealm(tenantId);
            }
        }

        ctx.updateDeployment(cfg);

        deployment = ctx.resolveDeployment(facade);

        System.out.println("Updated Realm: " + deployment.getRealm());
        adapterTokenStoreFactory.createAdapterTokenStore(deployment, request, response).logout();
        RefreshableKeycloakSecurityContext session = (RefreshableKeycloakSecurityContext) authenticationToken.getAccount().getKeycloakSecurityContext();
        session.logout(deployment);
    }

    public static Map<String, String> convertQueryStringToMap(String queryString) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String query = queryString;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > -1) {
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return query_pairs;
    }
}
