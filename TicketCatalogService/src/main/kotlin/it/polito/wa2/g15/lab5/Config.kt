package it.polito.wa2.g15.lab5

import it.polito.wa2.g15.lab5.security.JwtUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Configuration
class Config {
    @Autowired
    lateinit var jwtUtils: JwtUtils

    @Bean
    fun generateClient(): WebClient {

        return WebClient.builder()
                .baseUrl("http://localhost:8080")
                //.defaultCookie("cookieKey", "cookieValue")
                .defaultHeaders { headers ->
                        headers.contentType = MediaType.APPLICATION_JSON
                        headers.setBearerAuth(jwtUtils.generateJwtToken())
                        headers.set(HttpHeaders.ACCEPT_ENCODING, MediaType.APPLICATION_JSON_VALUE)
                }
                .defaultUriVariables(Collections.singletonMap("url", "http://localhost:8080"))
                .build()
    }

/*private fun httpHeaders(): Consumer<HttpHeaders> {
    return Consumer<HttpHeaders> { headers ->
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(jwtUtils.generateJwtToken())
        headers.set(HttpHeaders.ACCEPT_ENCODING, MediaType.APPLICATION_JSON_VALUE)
    }
}*/


}