package com.bancoxyz.bff.atm.client;

import com.bancoxyz.bff.atm.config.CoreServiceProperties;
import com.bancoxyz.bff.atm.exception.CoreServiceNoDisponibleException;
import com.bancoxyz.bff.atm.exception.CuentaNoEncontradaException;
import com.bancoxyz.bff.atm.exception.SaldoInsuficienteException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Unico punto de acceso de bff-atm hacia core-service. Agrega el encabezado
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

    /**
     * Confirma un retiro debitando el saldo en el backend generalizado (fuente unica de verdad
     * del dinero disponible). La validacion real de "saldo suficiente" ocurre en core-service,
     * no aqui: este BFF solo aplica sus propias reglas de canal (limite maximo por operacion,
     * ver {@code LimiteRetiroExcedidoException}) antes de reenviar la solicitud.
     */
    @CircuitBreaker(name = "coreService", fallbackMethod = "debitarSaldoFallback")
    public CuentaCoreDTO debitarSaldo(long cuentaId, double monto) {
        try {
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/cuentas/{cuentaId}/debito",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("monto", monto), cabecerasInternas()),
                    CuentaCoreDTO.class,
                    cuentaId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CuentaNoEncontradaException(cuentaId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar el retiro solicitado.");
        }
    }

    private CuentaCoreDTO debitarSaldoFallback(long cuentaId, double monto, Throwable t) {
        if (t instanceof CuentaNoEncontradaException || t instanceof SaldoInsuficienteException) {
            throw (RuntimeException) t;
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
