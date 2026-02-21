@Service
class LedgerService {
  @Transactional({ proxyTargetClass: true })
  record(amount: any) {
    return "ledger:" + amount;
  }
}

@Service
class OrderService {
  constructor(@Qualifier("ledgerServiceTsjComponent") ledger: any) {
    this.ledger = ledger;
  }

  @Transactional({ proxyTargetClass: true })
  place(amount: any) {
    return this.ledger.record(amount);
  }
}
