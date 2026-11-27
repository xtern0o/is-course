package ru.itmo.trace.identity

import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.*
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ActorResolver(private val identity: Identity) :
    WebMvcConfigurer, HandlerMethodArgumentResolver {
    override fun addInterceptors(
        registry: org.springframework.web.servlet.config.annotation.InterceptorRegistry
    ) {
        registry
            .addInterceptor(
                object : org.springframework.web.servlet.HandlerInterceptor {
                    override fun preHandle(
                        request: jakarta.servlet.http.HttpServletRequest,
                        response: jakarta.servlet.http.HttpServletResponse,
                        handler: Any,
                    ): Boolean {
                        val user =
                            (request.userPrincipal as? Authentication)?.principal as? OidcUser
                                ?: return true
                        request.setAttribute("trace.actor", identity.current(user))
                        return true
                    }
                }
            )
            .addPathPatterns("/api/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }

    override fun supportsParameter(parameter: MethodParameter) =
        parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        binder: WebDataBinderFactory?,
    ): Actor {
        return request.getAttribute("trace.actor", NativeWebRequest.SCOPE_REQUEST) as Actor
    }
}
