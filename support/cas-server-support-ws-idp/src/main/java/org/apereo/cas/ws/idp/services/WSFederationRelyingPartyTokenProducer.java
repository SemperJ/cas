package org.apereo.cas.ws.idp.services;

import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apereo.cas.ws.idp.web.WSFederationRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * This is {@link WSFederationRelyingPartyTokenProducer}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public interface WSFederationRelyingPartyTokenProducer {

    /**
     * Produce token for relying party.
     *
     * @param securityToken  the security token
     * @param service        the service
     * @param fedRequest     the fed request
     * @param servletRequest the servlet request
     * @return the element; token issues for the relying party
     */
    String produce(SecurityToken securityToken, WSFederationRegisteredService service,
                   WSFederationRequest fedRequest, HttpServletRequest servletRequest);
}
