package org.springframework.transaction;

public interface PlatformTransactionManager {
    TransactionStatus begin(String beanName, String methodName);

    void commit(TransactionStatus status);

    void rollback(TransactionStatus status);
}
