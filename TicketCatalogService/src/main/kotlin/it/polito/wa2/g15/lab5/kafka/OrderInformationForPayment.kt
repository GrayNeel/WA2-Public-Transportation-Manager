package it.polito.wa2.g15.lab5.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import it.polito.wa2.g15.lab5.dtos.PaymentInfo

data class OrderInformationForPayment(
        @JsonProperty("billing_info")
        val billingInfo: PaymentInfo,
        @JsonProperty("total_cost")
        val totalCost: Double
)