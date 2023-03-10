package it.polito.wa2.g15.lab4.repositories

import it.polito.wa2.g15.lab4.entities.TicketPurchased
import it.polito.wa2.g15.lab4.entities.UserDetails
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TicketPurchasedRepository : CrudRepository<TicketPurchased, Int>{
    fun findTicketPurchasedByUser(user: UserDetails): List<TicketPurchased>
    fun findTicketPurchasedByIatIsBetween(
        timeStart: Date,
        timeEnd: Date
    ): List<TicketPurchased>


    fun findTicketPurchasedByUserAndIatIsBetween(
        user: UserDetails,
        timeStart: Date,
        timeEnd: Date
    ): List<TicketPurchased>

    /**
     * For the moment the logic doesn't distinguish the absence of one single temporal constraint
     * i.e. no repo methods findBefore/findAfter are used
     */
    fun findTicketPurchasedByIatIsAfter(
        timeStart: Date
    ): List<TicketPurchased>
    fun findTicketPurchasedByIatIsBefore(
        timeEnd: Date
    ): List<TicketPurchased>
    /* These should work similarly
    fun countTicketPurchasedByUser(user: String): Long
    fun countTicketPurchasedByPurchaseTimeIsBetween(
        timeStart: Date,
        timeEnd: Date
    ): Long
    fun countTicketPurchasedByUserAndPurchaseTimeIsBetween(
        user: String,
        timeStart: Date,
        timeEnd: Date
    ): Long

     */

}