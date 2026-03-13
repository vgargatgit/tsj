import { Qualifier } from "java:org.springframework.beans.factory.annotation.Qualifier";
import { Service } from "java:org.springframework.stereotype.Service";
import { Transactional } from "java:org.springframework.transaction.annotation.Transactional";

@Service
class LedgerService {
  @Transactional({ proxyTargetClass: true })
  failRecord(amount: any) {
    throw "chain-failure:" + amount;
  }
}

@Service
class OrderService {
  ledger: any;

  constructor(@Qualifier("ledgerServiceTsjComponent") ledger: any) {
    this.ledger = ledger;
  }

  @Transactional({ proxyTargetClass: true })
  place(amount: any) {
    return this.ledger.failRecord(amount);
  }
}
