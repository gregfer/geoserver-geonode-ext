/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geonode.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.easymock.classextension.EasyMock;
import org.geonode.security.LayersGrantedAuthority.LayerMode;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.GeoServerUser;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Unit test suite for {@link DefaultSecurityClient}
 * 
 * @author groldan
 * 
 */
public class DefaultSecurityClientTest {

    private HTTPClient mockHttpClient;

    private DefaultSecurityClient client;

    @Before
    public void setUp() throws Exception {
        mockHttpClient = EasyMock.createNiceMock(HTTPClient.class);
        client = new DefaultSecurityClient("http://localhost:8000/", mockHttpClient);
    }

    @Test
    public void testSetApplicationContext() throws Exception {
        final String baseUrl = "http://127.0.0.1/fake";
        DefaultSecurityClient client2 = new DefaultSecurityClient(baseUrl, mockHttpClient);

        assertEquals(baseUrl, client2.getBaseUrl());
    }

    @Test
    public void testAuthenticateAnonymous() throws Exception {
        String response = "{'is_superuser': false, 'rw': [], 'ro': [], 'is_anonymous': true, 'name': ''}";
        EasyMock.expect(
                mockHttpClient.sendGET(EasyMock.eq("http://localhost:8000/layers/acls"),
                        (String[]) EasyMock.isNull())).andReturn(response);
        EasyMock.replay(mockHttpClient);

        Authentication authentication = client.authenticateAnonymous();
        EasyMock.verify(mockHttpClient);

        assertNotNull(authentication);
        assertTrue(authentication instanceof AnonymousAuthenticationToken);
        assertTrue(authentication.isAuthenticated());
        assertEquals("anonymous", authentication.getPrincipal());

        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.addAll(authentication.getAuthorities());
        assertEquals(3, authorities.size());
        assertTrue(authorities.get(0) instanceof LayersGrantedAuthority);
        assertEquals(LayerMode.READ_ONLY, ((LayersGrantedAuthority) authorities.get(0)).getAccessMode());
        assertEquals(0, ((LayersGrantedAuthority) authorities.get(0)).getLayerNames().size());

        assertEquals(LayerMode.READ_WRITE,
                ((LayersGrantedAuthority) authorities.get(1)).getAccessMode());
        assertEquals(0, ((LayersGrantedAuthority) authorities.get(1)).getLayerNames().size());

        assertTrue(authorities.get(2) instanceof GrantedAuthority);
        assertEquals("ROLE_ANONYMOUS", authorities.get(2).getAuthority());

    }

    @Test
    public void testAuthenticateCookie() throws Exception {
        final String cookieValue = "ABCD";
        final String[] requestHeaders = { "Cookie",
                GeoNodeCookieProcessingFilter.GEONODE_COOKIE_NAME + "=" + cookieValue };

        final String response = "{'is_superuser': true, 'rw': ['layer1', 'layer2'], 'ro': ['layer3'], 'is_anonymous': false, 'name': 'aang'}";

        EasyMock.expect(
                mockHttpClient.sendGET(EasyMock.eq("http://localhost:8000/layers/acls"),
                        EasyMock.aryEq(requestHeaders))).andReturn(response);
        EasyMock.replay(mockHttpClient);

        Authentication authentication = client.authenticateCookie(cookieValue);
        EasyMock.verify(mockHttpClient);

        assertNotNull(authentication);
        assertTrue(authentication instanceof GeoNodeSessionAuthToken);
        assertTrue(authentication.isAuthenticated());
        assertEquals("aang", ((GeoServerUser)authentication.getPrincipal()).getUsername());

        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.addAll(authentication.getAuthorities());
        assertEquals(4, authorities.size());
        assertTrue(authorities.get(0) instanceof LayersGrantedAuthority);
        assertEquals(LayerMode.READ_ONLY, ((LayersGrantedAuthority) authorities.get(0)).getAccessMode());
        assertEquals(Collections.singletonList("layer3"),
                ((LayersGrantedAuthority) authorities.get(0)).getLayerNames());

        assertEquals(LayerMode.READ_WRITE,
                ((LayersGrantedAuthority) authorities.get(1)).getAccessMode());
        assertEquals(Arrays.asList("layer1", "layer2"),
                ((LayersGrantedAuthority) authorities.get(1)).getLayerNames());

        assertTrue(authorities.get(2) instanceof GrantedAuthority);
        assertTrue(authorities.contains(GeoServerRole.ADMIN_ROLE));
    }

    @Test
    public void testAuthenticateUserPassword() throws Exception {
        String username = "aang";
        String password = "katara";
        final String[] requestHeaders = { "Authorization",
                "Basic " + new String(Base64.encodeBase64((username + ":" + password).getBytes())) };

        final String response = "{'is_superuser': false, 'rw': ['layer1'], 'ro': ['layer2', 'layer3'],"
                + " 'is_anonymous': false, 'name': 'aang', 'fullname': 'Andy Ang',"
                + " 'email': 'a@ang.com'}";

        EasyMock.expect(
                mockHttpClient.sendGET(EasyMock.eq("http://localhost:8000/layers/acls"),
                        EasyMock.aryEq(requestHeaders))).andReturn(response);
        EasyMock.replay(mockHttpClient);

        Authentication authentication = client.authenticateUserPwd(username, password);
        EasyMock.verify(mockHttpClient);

        assertNotNull(authentication);
        assertTrue(authentication instanceof GeoNodeSessionAuthToken);
        assertTrue(authentication.isAuthenticated());
        GeoServerUser user = (GeoServerUser) authentication.getPrincipal();
        assertEquals("aang", user.getUsername());
        assertEquals("Andy Ang", user.getProperties().get("fullname"));
        assertEquals("a@ang.com", user.getProperties().get("email"));

        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.addAll(authentication.getAuthorities());
        assertEquals(3, authorities.size());
        assertTrue(authorities.contains(GeoServerRole.AUTHENTICATED_ROLE));
        assertTrue(authorities.get(0) instanceof LayersGrantedAuthority);
        assertEquals(LayerMode.READ_ONLY, ((LayersGrantedAuthority) authorities.get(0)).getAccessMode());
        assertEquals(Arrays.asList("layer2", "layer3"),
                ((LayersGrantedAuthority) authorities.get(0)).getLayerNames());

        assertEquals(LayerMode.READ_WRITE,
                ((LayersGrantedAuthority) authorities.get(1)).getAccessMode());
        assertEquals(Arrays.asList("layer1"),
                ((LayersGrantedAuthority) authorities.get(1)).getLayerNames());
    }
}
