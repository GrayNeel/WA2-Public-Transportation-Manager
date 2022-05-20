package it.polito.wa2.g15.lab5.repositories

import it.polito.wa2.g15.lab5.entities.TicketType
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TicketTypeRepository : CoroutineCrudRepository<TicketType, Long> {

}