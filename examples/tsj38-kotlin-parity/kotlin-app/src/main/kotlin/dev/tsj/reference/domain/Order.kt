package dev.tsj.reference.domain

data class Order(
    val id: String,
    val customer: String,
    val total: Double
)
