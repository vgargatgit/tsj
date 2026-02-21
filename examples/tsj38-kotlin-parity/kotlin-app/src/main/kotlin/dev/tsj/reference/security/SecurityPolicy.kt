package dev.tsj.reference.security

class SecurityPolicy {
    fun canWrite(role: String): Boolean = role == "admin"

    fun canRead(role: String): Boolean = role == "admin" || role == "reader"
}
