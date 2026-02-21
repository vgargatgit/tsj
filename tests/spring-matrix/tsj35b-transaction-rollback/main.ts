@Service
class LedgerService {
  @Transactional({ proxyTargetClass: true })
  failRecord(amount: any) {
    throw "chain-failure:" + amount;
  }
}

@Service
class OrderService {
  constructor(@Qualifier("ledgerServiceTsjComponent") ledger: any) {
    this.ledger = ledger;
  }

  @Transactional({ proxyTargetClass: true })
  place(amount: any) {
    return this.ledger.failRecord(amount);
  }
}
