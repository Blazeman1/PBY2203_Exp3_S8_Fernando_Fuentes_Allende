package com.bancoxyz.bff.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Configuracion del servidor de autorizacion OAuth2.0 (Spring Authorization Server) de la
 * Semana 8. Alcance deliberadamente acotado a <b>servicio-a-servicio</b> (ver README, seccion
 * 13): los 3 BFF son los unicos clientes OAuth2 registrados, cada uno con su propio
 * {@code client_id}/{@code client_secret} y el unico flujo habilitado es
 * {@code client_credentials} -el flujo pensado exactamente para comunicacion maquina-a-maquina
 * sin un usuario humano de por medio, a diferencia de {@code authorization_code} (que requiere
 * redireccion de navegador y una pagina de login, innecesarias aqui: el login real de cada canal
 * ya lo resuelve su propio BFF con JWT/sesion, sin cambios en esta entrega).
 *
 * <p>Por eso este servidor NO define un {@code UserDetailsService} ni una pagina de login: solo
 * expone los endpoints estandar de la especificacion OAuth2 ({@code /oauth2/token},
 * {@code /oauth2/jwks}, {@code /.well-known/oauth-authorization-server}), protegidos por la
 * autenticacion HTTP Basic del propio {@code client_id}/{@code client_secret} de cada BFF -no por
 * una sesion de usuario.</p>
 */
@Configuration
public class AuthorizationServerConfig {

    /** Scope unico que representa "puede llamar a la API interna de core-service". */
    public static final String SCOPE_CORE_SERVICE = "core-service.access";

    @Value("${auth-server.issuer-uri}")
    private String issuerUri;

    @Value("${auth-server.clientes.bff-web.secreto}")
    private String secretoBffWeb;

    @Value("${auth-server.clientes.bff-mobile.secreto}")
    private String secretoBffMobile;

    @Value("${auth-server.clientes.bff-atm.secreto}")
    private String secretoBffAtm;

    /**
     * Cadena de seguridad especifica de los endpoints OAuth2 (la que provee
     * {@link OAuth2AuthorizationServerConfigurer}), con precedencia sobre la cadena por defecto
     * de mas abajo. La autenticacion del cliente (Basic Auth con client_id/client_secret) la
     * resuelve el propio {@code authorizationServerConfigurer} antes de llegar a
     * {@code authorizeHttpRequests}; por eso "anyRequest().authenticated()" ya alcanza aqui.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()));

        return http.build();
    }

    /**
     * Cadena de seguridad para todo lo que NO es un endpoint OAuth2 (en la practica, solo
     * {@code /actuator/health}, usado por scripts/generar_evidencia.sh para verificar que este
     * servicio arranco correctamente).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Los 3 BFF, cada uno con su propio client_id/client_secret (a diferencia de la clave
     * X-Internal-Api-Key compartida que reemplaza esta entrega): permite revocar o rotar las
     * credenciales de un BFF sin afectar a los otros dos, y deja trazabilidad de qué canal
     * origino cada token (subject del JWT = client_id).
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient bffWeb = clienteServicioAServicio("bff-web-client", secretoBffWeb, passwordEncoder);
        RegisteredClient bffMobile = clienteServicioAServicio("bff-mobile-client", secretoBffMobile, passwordEncoder);
        RegisteredClient bffAtm = clienteServicioAServicio("bff-atm-client", secretoBffAtm, passwordEncoder);
        return new InMemoryRegisteredClientRepository(bffWeb, bffMobile, bffAtm);
    }

    private RegisteredClient clienteServicioAServicio(String clientId, String secreto, PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secreto))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(SCOPE_CORE_SERVICE)
                // 10 minutos: suficientemente corto para que un token robado tenga vida util
                // limitada, suficientemente largo para no forzar una renovacion en cada
                // peticion del BFF a core-service (ver CoreServiceTokenProvider, que cachea el
                // token hasta 10s antes de su expiracion).
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Par de claves RSA generado en memoria al arrancar (se pierde y se regenera en cada
     * reinicio del proceso, igual que el resto de los repositorios en memoria de este proyecto -
     * ver README seccion 6). Suficiente para el alcance academico: en un despliegue real, esta
     * clave viviria en un almacen persistente (HSM, KMS) para que los tokens ya emitidos sigan
     * siendo validables tras un reinicio del auth-server.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generarParDeClaves();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generarParDeClaves() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA del auth-server.", ex);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * El "issuer" es el identificador publico de este servidor, incrustado en cada token emitido
     * (claim {@code iss}) y usado por core-service (Resource Server) para descubrir
     * automaticamente el endpoint de JWK Set (via {@code /.well-known/oauth-authorization-server})
     * y para validar que el token efectivamente proviene de este auth-server. Se resuelve por
     * configuracion (localhost en ejecucion local, nombre de contenedor en docker-compose) en
     * vez de hardcodearse - ver application.yml y application-docker.yml.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }
}

