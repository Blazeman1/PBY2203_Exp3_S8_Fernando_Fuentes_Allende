package com.bancoxyz.bff.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servidor de autorizacion OAuth2.0 de la Semana 8 (Spring Authorization Server).
 *
 * <p>A diferencia de config-server y eureka-server (infraestructura de la Semana 6, sin
 * dependencias entre si), este servicio NO se registra en Eureka ni consume config-server: un
 * servidor de autorizacion es, por diseno, un punto de confianza fijo y conocido de antemano por
 * todos sus clientes (los 3 BFF) - exactamente igual que Eureka y Config Server ya son
 * direcciones fijas (localhost:8761, localhost:8888) para el resto del ecosistema, en vez de
 * resolverse por descubrimiento de servicios. Agregarlo a Eureka no aportaria nada: ningun
 * cliente OAuth2 real busca su Authorization Server "por nombre logico", lo busca por su
 * {@code issuer-uri} configurado explicitamente (ver application.yml).</p>
 */
@SpringBootApplication
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
