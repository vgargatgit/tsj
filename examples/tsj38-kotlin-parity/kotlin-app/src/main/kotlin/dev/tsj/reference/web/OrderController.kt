package dev.tsj.reference.web

import dev.tsj.reference.service.OrderService

class OrderController(
    private val service: OrderService
) {
    fun list(role: String): Any = service.list(role)

    fun create(role: String, customer: String, total: Double): Any =
        service.create(role, customer, total)
}
