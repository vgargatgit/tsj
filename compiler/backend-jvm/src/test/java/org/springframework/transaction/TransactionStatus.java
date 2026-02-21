package org.springframework.transaction;

public record TransactionStatus(String beanName, String methodName) {
}
