package it.polito.wa2.g15.validatorservice.dtos

import org.springframework.security.core.GrantedAuthority

data class UserDetailsDTO(
    val sub: String,
    val roles: Set<GrantedAuthority>
)