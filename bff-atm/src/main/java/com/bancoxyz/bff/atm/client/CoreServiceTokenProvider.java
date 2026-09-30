package com.bancoxyz.bff.atm.client;

import com.bancoxyz.bff.atm.config.CoreServiceProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Obtiene y cachea el access token OAuth2.0 (flujo {@code client_credentials}) que bff-atm usa
 * para autenticarse ante core-service, en reemplazo de la antigua clave estatica compartida
 * {@code X-Internal-Api-Key}. Ver el equivalente en bff-web para la justificacion completa de
 * por que esta implementado a mano en vez de con {@code spring-boot-starter-oauth2-client}.
 *
 * <p>Usa una instancia de {@link RestTemplate} propia y deliberadamente <b>no</b>
 * {@code @LoadBalanced}: auth-server no esta registrado en Eureka (direccion fija conocida de
 * antemano), por lo que no hay un "nombre logico" que resolver -solo una URL de configuracion.</p>
 */
@Component
public class CoreServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceTokenProvider.class);

    private static final long SEGUNDOS_MARGEN_RENOVACION = 10;

    private final RestTemplate restTemplateAuthServer = new RestTemplate();
    private final CoreServiceProperties propiedades;

    private volatile String tokenCacheado;
    private volatile Instant expiraEn = Instant.MIN;

    public CoreServiceTokenProvider(CoreServiceProperties propiedades) {
        this.propiedades = propiedades;
    }

    public synchronized String obtenerToken() {
        if (tokenCacheado != null && Instant.now().isBefore(expiraEn)) {
            return tokenCacheado;
        }
        RespuestaToken respuesta = solicitarNuevoToken();
        this.tokenCacheado = respuesta.accessToken;
        this.expiraEn = Instant.now().plusSeconds(Math.max(0, respuesta.expiresIn - SEGUNDOS_MARGEN_RENOVACION));
        return tokenCacheado;
    }

    private RespuestaToken solicitarNuevoToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(propiedades.getOauth2().getClientId(), propiedades.getOauth2().getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        var respuesta = restTemplateAuthServer.postForEntity(
                propiedades.getOauth2().getTokenUri(),
                new HttpEntity<>(body, headers),
                RespuestaToken.class);

        RespuestaToken cuerpo = respuesta.getBody();
        if (cuerpo == null || cuerpo.accessToken == null) {
            throw new IllegalStateException("auth-server no devolvio un access_token valido.");
        }
        log.debug("Nuevo access token obtenido de auth-server (expira en {}s)", cuerpo.expiresIn);
        return cuerpo;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RespuestaToken {
        @JsonProperty("access_token")
        String accessToken;
        @JsonProperty("expires_in")
        long expiresIn;
    }
}
