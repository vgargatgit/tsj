package dev.tsj.reference.repository

import dev.tsj.reference.domain.Order

interface OrderRepository {
    fun list(): List<Order>
    fun save(customer: String, total: Double): Order
}
