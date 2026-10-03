package com.bancoxyz.bff.web.client;

import com.bancoxyz.bff.web.config.CoreServiceProperties;
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
 * Obtiene y cachea el access token OAuth2.0 (flujo {@code client_credentials}) que bff-web usa
 * para autenticarse ante core-service, en reemplazo de la antigua clave estatica compartida
 * {@code X-Internal-Api-Key}.
 *
 * <p>Implementado a mano con {@link RestTemplate} (en vez de
 * {@code spring-boot-starter-oauth2-client}) a proposito: este BFF ya resuelve su propia
 * autenticacion de canal (JWT del usuario web) con un filtro artesanal propio; introducir la
 * cadena de filtros de Spring Security solo para obtener un token de servicio-a-servicio
 * agregaria una segunda fuente de configuracion de seguridad a este mismo modulo, con riesgo de
 * interferir con la ya existente. Este componente hace, a mano, exactamente lo que ese starter
 * haria para {@code client_credentials}, sin tocar la cadena de filtros del BFF.</p>
 *
 * <p>Usa una instancia de {@link RestTemplate} propia y deliberadamente <b>no</b>
 * {@code @LoadBalanced}: a diferencia de core-service, auth-server no esta registrado en Eureka
 * (es una direccion fija conocida de antemano, ver el modulo auth-server), por lo que no hay un
 * "nombre logico" que resolver -solo una URL de configuracion.</p>
 */
@Component
public class CoreServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceTokenProvider.class);

    /** Colchon de renovacion anticipada: un token que expira en menos de esto ya no se reutiliza. */
    private static final long SEGUNDOS_MARGEN_RENOVACION = 10;

    private final RestTemplate restTemplateAuthServer = new RestTemplate();
    private final CoreServiceProperties propiedades;

    private volatile String tokenCacheado;
    private volatile Instant expiraEn = Instant.MIN;

    public CoreServiceTokenProvider(CoreServiceProperties propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Devuelve un access token valido: reutiliza el cacheado si aun le queda vigencia, o solicita
     * uno nuevo a auth-server en caso contrario. {@code synchronized} para que, bajo concurrencia,
     * dos peticiones simultaneas con el cache vencido no disparen dos solicitudes de token
     * innecesarias.
     */
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
        // Imprescindible: a diferencia de lo que podria asumirse, Spring Authorization Server NO
        // concede automaticamente todos los scopes configurados para el cliente (ver
        // AuthorizationServerConfig.clienteServicioAServicio) cuando una peticion
        // client_credentials omite el parametro "scope" -el token resultante queda SIN scope
        // alguno. core-service exige la autoridad SCOPE_core-service.access (ver
        // ResourceServerConfig), asi que sin esto cada llamada a core-service responde 403
        // insufficient_scope. Se detecto exactamente este caso en una corrida real del proyecto.
        body.add("scope", "core-service.access");

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
