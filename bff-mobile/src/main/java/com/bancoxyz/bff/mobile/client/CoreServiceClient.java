package com.bancoxyz.bff.mobile.client;

import com.bancoxyz.bff.mobile.config.CoreServiceProperties;
import com.bancoxyz.bff.mobile.exception.CoreServiceNoDisponibleException;
import com.bancoxyz.bff.mobile.exception.CuentaNoEncontradaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Unico punto de acceso de bff-mobile hacia core-service. Agrega el encabezado
 * {@code Authorization: Bearer <token>} de forma centralizada (Semana 8: reemplaza la antigua
 * clave compartida {@code X-Internal-Api-Key}).
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
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
