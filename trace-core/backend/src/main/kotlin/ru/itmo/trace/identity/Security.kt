package ru.itmo.trace.identity

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class Security {
    @Bean
    fun securityFilterChain(http: HttpSecurity, clients: ClientRegistrationRepository): SecurityFilterChain {
        val logout = OidcClientInitiatedLogoutSuccessHandler(clients)
        logout.setPostLogoutRedirectUri("{baseUrl}/")
        return http.authorizeHttpRequests {
            it.requestMatchers("/actuator/health", "/error").permitAll().anyRequest().authenticated()
        }.oauth2Login { it.defaultSuccessUrl("/", true) }
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    { _, response, _ -> response.sendError(401) }, AntPathRequestMatcher("/api/**")
                )
            }
            .logout { it.logoutSuccessHandler(logout) }.build()
    }
}
