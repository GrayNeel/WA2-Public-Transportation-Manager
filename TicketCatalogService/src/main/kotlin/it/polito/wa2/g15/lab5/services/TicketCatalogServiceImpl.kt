package it.polito.wa2.g15.lab5.services

import com.netflix.discovery.EurekaClient
import it.polito.wa2.g15.lab5.dtos.BuyTicketDTO
import it.polito.wa2.g15.lab5.dtos.NewTicketItemDTO
import it.polito.wa2.g15.lab5.entities.TicketItem
import it.polito.wa2.g15.lab5.entities.TicketOrder
import it.polito.wa2.g15.lab5.exceptions.InvalidTicketOrderException
import it.polito.wa2.g15.lab5.exceptions.InvalidTicketRestrictionException
import it.polito.wa2.g15.lab5.kafka.OrderInformationMessage
import it.polito.wa2.g15.lab5.repositories.TicketItemRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TicketCatalogServiceImpl : TicketCatalogService {
    @Autowired
    lateinit var ticketItemRepository: TicketItemRepository

    lateinit var ticketItemsCache : MutableList<TicketItem>

    @Autowired
    lateinit var ticketOrderService: TicketOrderService

    @Autowired
    lateinit var discoveryClient : EurekaClient

    @Value("\${kafka.topics.produce}")
    lateinit var topic: String

    @Value("\${ticket.catalog.cache}")
    lateinit var ticketCatalogCacheStatus :String

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, OrderInformationMessage>

    private val logger = KotlinLogging.logger {}

    //Defined in config class
    @Autowired
    lateinit var client : WebClient

    private fun isCacheEnabled():Boolean{
        return ticketCatalogCacheStatus=="enabled"
    }

    override suspend fun initTicketCatalogCache() {
        runBlocking {
            if (ticketCatalogCacheStatus == "enabled") {
                logger.info { "start initialization ticketItem cache ..." }
                ticketItemsCache = ticketItemRepository.findAll().toList().toMutableList()
                logger.info { "... initialization ticketItem cache finished" }
                logger.info { "these are the ticket found in the catalog during the startup:\n $ticketItemsCache" }
            } else ticketItemsCache = mutableListOf()
        }
    }
    override fun getAllTicketItems(): Flow<TicketItem> {
        return if(isCacheEnabled())
            ticketItemsCache.asFlow()
        else
            ticketItemRepository.findAll()
    }

    override suspend fun addNewTicketType(newTicketItemDTO: NewTicketItemDTO) : Long {
        var ticketItem = TicketItem(
            ticketType = newTicketItemDTO.type,
            price = newTicketItemDTO.price,
            minAge = newTicketItemDTO.minAge,
            maxAge = newTicketItemDTO.maxAge,
            duration = newTicketItemDTO.duration,
            // new TicketItems has available=true by default
        )

        try {
            ticketItem=ticketItemRepository.save(ticketItem)
            if(isCacheEnabled()) {
                logger.info { "Updating cache..." }
                ticketItemsCache.add(ticketItem)
            }
        } catch (e: Exception) {
            throw Exception("Failed saving ticketItem: ${e.message}")
        }
        
        return ticketItem.id ?: throw InvalidTicketOrderException("order id not saved correctly in the db")
    }

    override suspend fun removeTicketType(ticketId: Long): Boolean {
        /* Retrieve the old ticket item */
        val ticketToDelete : TicketItem?
        try {
            ticketToDelete = if(isCacheEnabled())
                ticketItemsCache.find { it.id == ticketId}
            else
                ticketItemRepository.findById(ticketId)
        }catch (e: Exception) {
            throw Exception("Failed deleting ticketItem: ${e.message}")
        }
        if (ticketToDelete == null)
            throw Exception("Failed deleting ticketItem: no ticket with such id")
        /* Mark and update the old item type as unavailable to be generated */
        if(isCacheEnabled()) {
            logger.info { "Updating cache..." }
            //Could be useful remove the old one?
            //To remove after changing 'available' field, I should need a copy of the original item
            ticketItemsCache.remove(ticketToDelete)
        }
        ticketToDelete.available = false
        ticketItemRepository.save(ticketToDelete)
        //It's not necessary that this method return something... the controller use the exception as false result
        return true
    }

    override suspend fun modifyTicketType(ticketId: Long, newTicketItemDTO: NewTicketItemDTO): Long {
        /* Retrieve the old ticket item */
        val ticketToModify : TicketItem?
        try {
            ticketToModify = if (isCacheEnabled())
                ticketItemsCache.find { it.id == ticketId }
            else
                ticketItemRepository.findById(ticketId)
        } catch (e: Exception) {
            throw InvalidTicketRestrictionException("Failed modifying ticketItem: ${e.message}")
        }
    
        if (ticketToModify == null)
            throw InvalidTicketRestrictionException("Failed modifying ticketItem: no ticket with such id")
        if (!ticketToModify.available)
            throw InvalidTicketRestrictionException("Failed modifying ticketItem: can't modify old tickets")
        /* Create the new ticket item with the provided details */
        val newId = addNewTicketType(newTicketItemDTO)
        //The addNewTicket add also to the cache
        /* Mark and update the old item type as unavailable to be generated */
        if (isCacheEnabled()) {
            logger.info { "Updating cache..." }
            ticketItemsCache.remove(ticketToModify)
        }
        ticketToModify.available = false
        ticketItemRepository.save(ticketToModify)   //This update the old entity
        return newId
    }

    private fun checkRestriction(userAge: Int, ticketRequested: TicketItem): Boolean {
        if(userAge>ticketRequested.minAge!! && userAge<ticketRequested.maxAge!!)
            return true
        return false
    }

    override suspend fun buyTicket(buyTicketDTO: BuyTicketDTO, ticketId: Long, userName: String): Long = coroutineScope()
    {
        logger.info("ctx: ${this.coroutineContext.job} \t start buying info ")
        val ticketRequested =
                withContext(Dispatchers.IO + CoroutineName("find ticket")) {
                    logger.info("ctx:  ${this.coroutineContext.job} \t searching ticket info")
                    ticketItemRepository.findById(ticketId) ?: throw InvalidTicketOrderException("Ticket Not Found")
                }

        /* Check if the ticket requested is no longer for sale (updated or deleted) */
        if (!ticketRequested.available)
            throw  InvalidTicketRestrictionException("Ticket with id: $ticketId is no longer for sale (updated or deleted). Select a new one")

        if (ticketHasRestriction(ticketRequested)) {
            val travelerAge =
                    //async(Dispatcher.IO + CoroutineName("find user age")
                    withContext(Dispatchers.IO + CoroutineName("find user age")) {
                        logger.info("ctx:  ${this.coroutineContext.job} \t searching user age")
                        getTravelerAge(userName)
                    }
            if (!checkRestriction(travelerAge, ticketRequested))
                throw InvalidTicketRestrictionException("User $userName is $travelerAge years old and can not buy" +
                        " ticket $ticketId")

        }

        val ticketPrice = ticketRequested.price * buyTicketDTO.zid.length
        val totalPrice = buyTicketDTO.numOfTickets * ticketPrice

        logger.info("ctx: ${this.coroutineContext.job}\t order request received from user $userName for ${buyTicketDTO.numOfTickets} ticket $ticketId" +
                "\n the user want to pay with ${buyTicketDTO.paymentInfo}" +
                "\n\t totalPrice = $totalPrice")

        val order = withContext(Dispatchers.IO + CoroutineName("save pending order")) {

            ticketOrderService.savePendingOrder(
                    totalPrice = totalPrice,
                    username = userName,
                    ticketId = ticketId,
                    quantity = buyTicketDTO.numOfTickets,
                    validFrom = buyTicketDTO.validFrom,
                    zid = buyTicketDTO.zid
            )
        }
        logger.info("order $order set pending")

        //This could throw an exception
        publishOrderOnKafka(buyTicketDTO, order)


        order.orderId ?: throw InvalidTicketOrderException("order id not saved correctly in the db")

    }

    private suspend fun getTravelerAge(userName: String): Int {
        val age : Long
        try {
            val instanceInfo = discoveryClient.getNextServerFromEureka("traveler", false)
            val homePageUrl = instanceInfo.homePageUrl

            age = client.get()
                .uri(homePageUrl + "services/user/$userName/birthdate/")
                .awaitExchange {
                    if (it.statusCode() != HttpStatus.OK)
                        throw InvalidTicketRestrictionException("User info not found")
                    LocalDate.now()
                    ChronoUnit.YEARS.between(it.bodyToMono<LocalDate>().awaitSingle(), LocalDate.now())
                }

        }catch(e: RuntimeException) {
            throw InvalidTicketRestrictionException("Traveler service not found")
        }/*
            This is caught in the controller
            catch(e: WebClientResponseException) {
            throw InvalidTicketRestrictionException("Traveler service not responding properly")
        }*/

        logger.info { "User ($userName) age is: $age" }
        return age.toInt()
    }


    /**
     * publish on kafka the event of the pending order
     */
    private fun publishOrderOnKafka(buyTicketDTO: BuyTicketDTO, ticketOrder: TicketOrder) {
        val message: Message<OrderInformationMessage> = MessageBuilder
                .withPayload(OrderInformationMessage(buyTicketDTO.paymentInfo, ticketOrder.totalPrice, ticketOrder.username, ticketOrder.orderId!!))
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader("X-Custom-Header", "Custom header here")
                .build()
        kafkaTemplate.send(message)
        logger.info("Message sent with success on topic: $topic")
    }

    //Consume message is in order service


    /**
     * if the ticket has some restriction about the age or stuff like that return true, false otherwise
     */
    private fun ticketHasRestriction(ticket : TicketItem): Boolean {

        if(ticket.minAge == null && ticket.maxAge == null)
            return false
        else
            if(ticket.minAge != null && ticket.maxAge != null)
                if(ticket.minAge > ticket.maxAge)
                    throw InvalidTicketRestrictionException("ticket restriction is not valid, min age = ${ticket.minAge} > max age = ${ticket.maxAge}")

        return true
    }
}