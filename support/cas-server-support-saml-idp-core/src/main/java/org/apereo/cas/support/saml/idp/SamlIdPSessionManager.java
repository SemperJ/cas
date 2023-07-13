package org.apereo.cas.support.saml.idp;

import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlIdPConstants;
import org.apereo.cas.support.saml.SamlUtils;
import org.apereo.cas.support.saml.authentication.SamlIdPAuthenticationContext;
import org.apereo.cas.util.EncodingUtils;
import org.apereo.cas.util.function.FunctionUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.shibboleth.shared.codec.Base64Support;
import org.apache.commons.lang3.tuple.Pair;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestAbstractType;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.jee.context.session.JEESessionStore;
import java.io.ByteArrayInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * This is {@link SamlIdPSessionManager}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class SamlIdPSessionManager {
    private final OpenSamlConfigBean openSamlConfigBean;
    private final SessionStore sessionStore;

    /**
     * Build the saml idp session manager.
     *
     * @param openSamlConfigBean the open saml config bean
     * @param sessionStore       the session store
     * @return the saml id p session manager
     */
    public static SamlIdPSessionManager of(final OpenSamlConfigBean openSamlConfigBean,
                                           final SessionStore sessionStore) {
        return new SamlIdPSessionManager(openSamlConfigBean, sessionStore);
    }

    /**
     * Build the saml idp session manager.
     *
     * @param openSamlConfigBean the open saml config bean
     * @return the saml id p session manager
     */
    public static SamlIdPSessionManager of(final OpenSamlConfigBean openSamlConfigBean) {
        return new SamlIdPSessionManager(openSamlConfigBean, JEESessionStore.INSTANCE);
    }

    /**
     * Store saml request.
     *
     * @param webContext the web context
     * @param context    the context
     * @throws Exception the exception
     */
    public void store(final WebContext webContext,
                      final Pair<? extends SignableSAMLObject, MessageContext> context) throws Exception {
        val authnRequest = (AuthnRequest) context.getLeft();
        val messageContext = context.getValue();
        try (val writer = SamlUtils.transformSamlObject(openSamlConfigBean, authnRequest)) {
            val samlRequest = EncodingUtils.encodeBase64(writer.toString().getBytes(StandardCharsets.UTF_8));
            val authnContext = SamlIdPAuthenticationContext.from(messageContext).encode();
            val entry = new SamlIdPSessionEntry()
                .setId(authnRequest.getID())
                .setSamlRequest(samlRequest)
                .setRelayState(SAMLBindingSupport.getRelayState(messageContext))
                .setContext(authnContext);
            val currentContext = sessionStore.get(webContext, SamlIdPSessionEntry.class.getName());
            val entries = currentContext.map(ctx -> (Map<String, SamlIdPSessionEntry>) ctx).orElseGet(HashMap::new);
            entries.put(entry.getId(), entry);
            sessionStore.set(webContext, SamlIdPSessionEntry.class.getName(), entries);
        }
    }

    /**
     * Retrieve authn request authn request.
     *
     * @param context the context
     * @param clazz   the clazz
     * @return the request
     */
    public Optional<Pair<? extends RequestAbstractType, MessageContext>> fetch(
        final WebContext context, final Class<? extends RequestAbstractType> clazz) {
        val currentContext = sessionStore.get(context, SamlIdPSessionEntry.class.getName());
        return currentContext.map(ctx -> (Map<String, SamlIdPSessionEntry>) ctx)
            .flatMap(ctx -> context.getRequestParameter(SamlIdPConstants.AUTHN_REQUEST_ID).map(ctx::get))
            .map(value -> {
                val authnRequest = fetch(clazz, value.getSamlRequest());
                val messageContext = SamlIdPAuthenticationContext.decode(value.getContext()).toMessageContext(authnRequest);
                return Pair.of((AuthnRequest) messageContext.getMessage(), messageContext);
            });
    }

    /**
     * Retrieve saml request.
     *
     * @param <T>          the type parameter
     * @param clazz        the clazz
     * @param requestValue the request value
     * @return the t
     */
    public <T extends RequestAbstractType> T fetch(final Class<T> clazz, final String requestValue) {
        try {
            LOGGER.trace("Retrieving SAML request from [{}]", requestValue);
            val decodedBytes = Base64Support.decode(requestValue);
            try (val is = new InflaterInputStream(new ByteArrayInputStream(decodedBytes), new Inflater(true))) {
                return clazz.cast(XMLObjectSupport.unmarshallFromInputStream(openSamlConfigBean.getParserPool(), is));
            }
        } catch (final Exception e) {
            return FunctionUtils.doUnchecked(() -> {
                val encodedRequest = EncodingUtils.decodeBase64(requestValue.getBytes(StandardCharsets.UTF_8));
                try (val is = new ByteArrayInputStream(encodedRequest)) {
                    return clazz.cast(XMLObjectSupport.unmarshallFromInputStream(openSamlConfigBean.getParserPool(), is));
                }
            });
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(chain = true)
    private static class SamlIdPSessionEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 8119055575574523810L;

        private String id;

        private String samlRequest;

        private String relayState;

        private String context;
    }
}
