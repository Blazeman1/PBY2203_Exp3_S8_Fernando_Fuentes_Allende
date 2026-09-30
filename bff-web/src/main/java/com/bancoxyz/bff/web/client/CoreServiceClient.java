package com.bancoxyz.bff.web.client;

import com.bancoxyz.bff.web.config.CoreServiceProperties;
import com.bancoxyz.bff.web.exception.CoreServiceNoDisponibleException;
import com.bancoxyz.bff.web.exception.CuentaNoEncontradaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

/**
 * Unico punto de acceso de bff-web hacia el backend generalizado (core-service). Ningun
 * controlador de este modulo llama a {@code RestTemplate} directamente: todos pasan por aqui,
 * que es quien agrega el encabezado {@code Authorization: Bearer <token>} de forma centralizada
 * (Semana 8: reemplaza la antigua clave compartida {@code X-Internal-Api-Key}).
 */
@Component
public class CoreServiceClient {

    private final RestTemplate restTemplate;
    private final CoreServiceProperties propiedades;
    private final CoreServiceTokenProvider tokenProvider;

    public CoreServiceClient(RestTemplate restTemplate, CoreServiceProperties propiedades,
                              CoreServiceTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.propiedades = propiedades;
        this.tokenProvider = tokenProvider;
    }

    @CircuitBreaker(name = "coreService", fallbackMethod = "obtenerCuentaFallback")
    public CuentaCoreDTO obtenerCuenta(long cuentaId) {
        try {
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/cuentas/{cuentaId}",
                    HttpMethod.GET,
                    new HttpEntity<>(cabecerasInternas()),
                    CuentaCoreDTO.class,
                    cuentaId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CuentaNoEncontradaException(cuentaId);
        }
    }

    private CuentaCoreDTO obtenerCuentaFallback(long cuentaId, Throwable t) {
        if (t instanceof CuentaNoEncontradaException) {
            throw (CuentaNoEncontradaException) t;
        }
        throw new CoreServiceNoDisponibleException(
                "core-service no disponible en este momento, intente mas tarde.", t);
    }

    private HttpHeaders cabecerasInternas() {
        HttpHeaders headers = new HttpHeaders();
        // Si auth-server no responde, tokenProvider.obtenerToken() lanza una excepcion que se
        // propaga tal cual al metodo @CircuitBreaker que llamo a este cliente: un token no
        // obtenido y un core-service caido producen, a proposito, el mismo efecto observable
        // (fallback de "core-service no disponible"), sin necesidad de un circuit breaker aparte.
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
