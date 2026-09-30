package com.bancoxyz.bff.atm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "core-service")
public class CoreServiceProperties {

    private String baseUrl = "http://core-service";

    @NestedConfigurationProperty
    private OAuth2 oauth2 = new OAuth2();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    /**
     * Credenciales OAuth2.0 (flujo client_credentials) que este BFF usa para autenticarse ante
     * core-service a traves de auth-server (Semana 8), en reemplazo de la antigua clave
     * compartida X-Internal-Api-Key. Ver {@link com.bancoxyz.bff.atm.client.CoreServiceTokenProvider}.
     */
    public static class OAuth2 {
        private String tokenUri = "http://localhost:9000/oauth2/token";
        private String clientId;
        private String clientSecret;

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
