package dev.tsj.reference

import dev.tsj.reference.repository.InMemoryOrderRepository
import dev.tsj.reference.security.SecurityPolicy
import dev.tsj.reference.service.OrderService
import dev.tsj.reference.web.OrderController

object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        val repository = InMemoryOrderRepository()
        val policy = SecurityPolicy()
        val service = OrderService(repository, policy)
        val controller = OrderController(service)
        println(controller.list("reader"))
    }
}
