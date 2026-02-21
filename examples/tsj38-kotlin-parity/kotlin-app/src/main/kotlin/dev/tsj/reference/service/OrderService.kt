package dev.tsj.reference.service

import dev.tsj.reference.repository.OrderRepository
import dev.tsj.reference.security.SecurityPolicy

class OrderService(
    private val repository: OrderRepository,
    private val policy: SecurityPolicy
) {
    fun list(role: String): Any {
        if (!policy.canRead(role)) {
            throw IllegalStateException("forbidden")
        }
        return repository.list()
    }

    fun create(role: String, customer: String, total: Double): Any {
        if (!policy.canWrite(role)) {
            throw IllegalStateException("forbidden")
        }
        return repository.save(customer, total)
    }
}
