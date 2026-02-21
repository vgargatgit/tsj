package dev.tsj.reference.repository

import dev.tsj.reference.domain.Order

class InMemoryOrderRepository : OrderRepository {
    private val orders = mutableListOf<Order>()
    private var nextId = 1

    override fun list(): List<Order> = orders.toList()

    override fun save(customer: String, total: Double): Order {
        val order = Order("order-$nextId", customer, total)
        nextId += 1
        orders.add(order)
        return order
    }
}
