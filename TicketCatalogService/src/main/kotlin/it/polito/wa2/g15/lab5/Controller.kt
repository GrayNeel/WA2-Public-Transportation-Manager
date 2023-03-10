package it.polito.wa2.g15.lab5

import it.polito.wa2.g15.lab5.dtos.*
import it.polito.wa2.g15.lab5.entities.TicketOrder
import it.polito.wa2.g15.lab5.exceptions.InvalidTicketRestrictionException
import it.polito.wa2.g15.lab5.services.TicketCatalogService
import it.polito.wa2.g15.lab5.services.TicketOrderService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.stream.Collectors
import javax.validation.Valid

@RestController
class Controller {
    @Autowired
    private lateinit var ticketCatalogService: TicketCatalogService
    
    @Autowired
    private lateinit var ticketOrderService: TicketOrderService
    
    private val principal = ReactiveSecurityContextHolder.getContext()
        .map { obj: SecurityContext -> obj.authentication.principal }
        .cast(UserDetailsDTO::class.java)

    /**
     * Returns a JSON representation of all available tickets. Those tickets
     * are represented as a JSON object consisting of price, ticketId, type ( ordinal or type
     * of pass).
     */
    @GetMapping("tickets/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun availableTickets(): Flow<TicketItemDTO> {
        return ticketCatalogService.getAllTicketItems().map { item -> item.toDTO() }
    }
    
    /**
     * It accepts a json containing the number of tickets, ticketId,
     * and payment information (credit card number, expiration date, cvv, card holder). Only
     * authenticated users can perform this request. In case those tickets have age
     * restrictions, it asks the TravellerService the user profile in order to check if the
     * operation is permitted. In case it is, it saves the order in the database with the
     * PENDING status, then it transmits the billing information and the total cost of the
     * order to the payment service through a kafka topic, and it returns the orderId. When
     * the Kafka listener receives the outcome of the transaction, the status of order is
     * updated according to what the payment service has stated and, if the operation was
     * successful, the purchased products are added to the list of acquired tickets in the
     * TravellerService.
     * The client to check the order result, must do polling to check the outcome.
     */
    @PostMapping("/shop/{ticket-id}/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN','CUSTOMER')")
    suspend fun buyTickets(
        @PathVariable("ticket-id") ticketId: Long,
        @Valid @RequestBody buyTicketBody: BuyTicketDTO,
        response: ServerHttpResponse
    ): Long? {
        
        val userName = principal.map { p -> p.sub }.awaitSingle()
        
        val res: Long? = try {
            response.statusCode = HttpStatus.ACCEPTED
            ticketCatalogService.buyTicket(buyTicketBody, ticketId, userName)
        } catch (e: WebClientResponseException){
            // if the TravelerService is not available, is not available the check on the age restrictions
            response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
            null
        }
        catch (e: Exception) {
            response.statusCode = HttpStatus.BAD_REQUEST
            null
        }
        return res
    }
    
    /**
     * Get the orders of the user
     */
    @GetMapping("orders/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN','CUSTOMER')")
    suspend fun orders(response: ServerHttpResponse): Flow<TicketOrder> {
        val userName = principal.map { p -> p.sub }
        return ticketOrderService.getUserTicketOrders(userName.awaitSingle()).onEmpty {
            response.statusCode = HttpStatus.NOT_FOUND
        }
    }
    
    /**
     * Get a specific order. This endpoint can be used by the client
     * to check the order status after a purchase.
     */
    @GetMapping("orders/{order-id}/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN','CUSTOMER')")
    suspend fun getSpecificOrder(
        @PathVariable("order-id") orderId: Long,
        response: ServerHttpResponse
    ): TicketOrder? {
        println("PathVariable orderId=$orderId")
        val result = ticketOrderService.getTicketOrderById(orderId, principal.map { it.sub }.awaitSingle())
        if (result == null) {
            response.statusCode = HttpStatus.NOT_FOUND
        }
        return result
    }
    
    /**
     * Admin users can add to catalog new available tickets to purchase.
     */
    @PostMapping("admin/tickets/")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN')")
    suspend fun addNewAvailableTicketToCatalog(
            @Valid @RequestBody newTicketItemDTO: NewTicketItemDTO,
            response: ServerHttpResponse
    ): Long? {
        
        val res: Long? = try {
            response.statusCode = HttpStatus.ACCEPTED
            ticketCatalogService.addNewTicketType(newTicketItemDTO)
        } catch (e: InvalidTicketRestrictionException) {
            response.statusCode = HttpStatus.BAD_REQUEST
            null
        }
        return res
    }
    
    /**
     * Admin users can delete from catalog tickets to purchase.
     */
    @DeleteMapping("admin/tickets/{ticket-id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN')")
    suspend fun removeTicketFromCatalog(
        @PathVariable("ticket-id") ticketId: Long,
        response: ServerHttpResponse
    ) {
        try {
            response.statusCode = HttpStatus.ACCEPTED
            ticketCatalogService.removeTicketType(ticketId)
        } catch (e: Exception) {
            response.statusCode = HttpStatus.BAD_REQUEST
        }
    }
    
    /**
     * Admin users can modify a ticket already present into the catalog.
     * Receive a newTicketItemDTO and substitute with the old one
     * @param: the id of the old ticketItem to edit
     * @return: the id of the newer ticketItem
     */
    @PutMapping("admin/tickets/{ticket-id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN')")
    suspend fun modifyTicketInCatalog(
        @PathVariable("ticket-id") ticketId: Long,
        @Valid @RequestBody newTicketItemDTO: NewTicketItemDTO,
        response: ServerHttpResponse
    ): Long? {

        val res: Long? = try {
            response.statusCode = HttpStatus.ACCEPTED
            ticketCatalogService.modifyTicketType(ticketId, newTicketItemDTO)
        } catch (e: Exception) {
            response.statusCode = HttpStatus.BAD_REQUEST
            null
        }
        return res
    }
    
    /**
     * This endpoint retrieves a list of all orders made by all users
     */
    @GetMapping("admin/orders/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN')")
    suspend fun getAllOrder(): Flow<TicketOrder> {
        return ticketOrderService.getAllTicketOrders()
    }
    
    /**
     * Get orders of a specific user
     */
    @GetMapping("admin/orders/{user-id}/", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERADMIN')")
    suspend fun getOrdersOfASpecificUser(@PathVariable("user-id") userId: String): Flow<TicketOrder> {
        return ticketOrderService.getUserTicketOrders(userId)
    }
    
    /*Endpoint to test kafka communication with the payment service*/
    
    /*
    This Doesn't work anymore because the listener of the topic in payment now does other things
    @Value("\${kafka.topics.produce}")
    lateinit var topic: String
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, OrderInformationMessage>

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("test/kafka/produce/")
    fun testKafkaProduceMessage(@Validated @RequestBody product: OrderInformationMessage, response: ServerHttpResponse) {
        return try {
            log.info("Receiving product request")
            log.info("Sending message to Kafka {}", product)
            val message: Message<OrderInformationMessage> = MessageBuilder
                    .withPayload(product)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader("X-Custom-Header", "Custom header here")
                    .build()
            kafkaTemplate.send(message)
            log.info("Message sent with success on topic: $topic")
            response.statusCode = HttpStatus.OK
        } catch (e: Exception) {
            log.error("Exception: {}",e)
            response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
        }
    }*/
}

@ControllerAdvice
class ValidationHandler {
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleException(e: WebExchangeBindException): ResponseEntity<List<String?>> {
        val errors = e.bindingResult
                .allErrors
                .stream()
                .map { obj: ObjectError -> obj.defaultMessage }
                .collect(Collectors.toList())
        return ResponseEntity.badRequest().body(errors)
    }
}