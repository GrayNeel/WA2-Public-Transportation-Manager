package it.polito.wa2.g15.validatorservice.security


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@EnableWebSecurity
class

WebSecurityConfig : WebSecurityConfigurerAdapter() {

    @Autowired
    private lateinit var jwtFilter: JWTAuthenticationFilter

    @Autowired
    private val unauthorizedHandler: AuthEntryPointJwt? = null

    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository {
        // A CsrfTokenRepository that persists the CSRF token in a cookie named "XSRF-TOKEN" and reads from the header
        // "X-XSRF-TOKEN" following the conventions of AngularJS. When using with AngularJS be sure to use
        // withHttpOnlyFalse().
        return CookieCsrfTokenRepository.withHttpOnlyFalse()
    }

    fun generateCsrfHeader(csrfTokenRepository: CsrfTokenRepository): HttpHeaders {

        val headers = HttpHeaders()
        val csrfToken: CsrfToken = csrfTokenRepository.generateToken(null)

        headers.add(csrfToken.headerName, csrfToken.token)
        headers.add("Cookie", "XSRF-TOKEN=" + csrfToken.token)

        return headers
    }

    override fun configure(http: HttpSecurity) {
        http.csrf()
            .disable()
            //.csrfTokenRepository(csrfTokenRepository())

        http.exceptionHandling().authenticationEntryPoint(unauthorizedHandler)

        http.sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        http.authorizeRequests()
            .antMatchers("/get/stats").hasAnyAuthority("ADMIN", "SUPERADMIN")
            .anyRequest().permitAll()

        http.addFilterAt(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
    }

}