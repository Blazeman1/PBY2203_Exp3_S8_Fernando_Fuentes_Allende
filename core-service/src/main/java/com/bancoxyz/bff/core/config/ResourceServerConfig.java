package com.bancoxyz.bff.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion de seguridad de core-service como <b>OAuth2 Resource Server</b> (Semana 8), en
 * reemplazo de {@code InternalApiKeyFilter} (la clave estatica compartida {@code
 * X-Internal-Api-Key} usada desde la Semana 4).
 *
 * <p>El principio del patron BFF que ese filtro materializaba no cambia: este servicio sigue sin
 * ser alcanzable por ningun frontend, solo por los tres BFF. Lo que cambia es el mecanismo: cada
 * BFF ahora se autentica ante {@code auth-server} con su propio {@code client_id}/{@code
 * client_secret} (flujo {@code client_credentials}) y adjunta el JWT resultante como {@code
 * Authorization: Bearer <token>}. Este servicio valida ese JWT automaticamente -firma, expiracion
 * y emisor- resolviendo las claves publicas de auth-server via {@code issuer-uri} (ver
 * application.yml / config-repo/core-service.yml), y ademas exige el scope {@code
 * core-service.access} para autorizar la peticion.</p>
 *
 * <p>Frente a la clave compartida anterior, esto permite revocar o rotar las credenciales de un
 * solo BFF sin afectar a los otros dos, y deja trazabilidad de que canal origino cada peticion
 * (claim {@code sub} del JWT = client_id del BFF), algo que la clave unica no distinguia.</p>
 */
@Configuration
public class ResourceServerConfig {

    /** Debe coincidir con el scope que auth-server otorga a los 3 BFF (ver AuthorizationServerConfig). */
    private static final String SCOPE_CORE_SERVICE = "core-service.access";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        // El endpoint de salud lo consultan los scripts de evidencia y, en
                        // docker-compose, el healthcheck del propio contenedor: no puede exigir
                        // un JWT que esas comprobaciones no tienen forma de obtener.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().hasAuthority("SCOPE_" + SCOPE_CORE_SERVICE))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Este servicio nunca se llama desde un navegador con cookies de sesion (solo
                // BFF-a-servidor con un Bearer token en el header), por lo que no hay superficie
                // CSRF que proteger aqui -igual que ya era el caso con InternalApiKeyFilter.
                .csrf(AbstractHttpConfigurer::disable)
                // Sin estado de sesion: cada peticion se autentica de cero con su propio JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS));

        return http.build();
    }

    /**
     * Spring Security antepone por defecto el prefijo {@code SCOPE_} a cada scope del JWT (claim
     * {@code scope}), quedando como autoridad {@code SCOPE_core-service.access}; este converter
     * solo lo hace explicito para que quede documentado en un unico lugar.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopesConverter);
        return converter;
    }
}
