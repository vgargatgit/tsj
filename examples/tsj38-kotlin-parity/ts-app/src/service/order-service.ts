import type { OrderRepository } from "../repository/order-repository";
import type { SecurityPolicy } from "../security/security-policy";

export class OrderService {
  private readonly repository: OrderRepository;
  private readonly policy: SecurityPolicy;

  constructor(repository: OrderRepository, policy: SecurityPolicy) {
    this.repository = repository;
    this.policy = policy;
  }

  list(role: string) {
    if (!this.policy.canRead(role)) {
      throw "forbidden";
    }
    return this.repository.list();
  }

  create(role: string, customer: string, total: number) {
    if (!this.policy.canWrite(role)) {
      throw "forbidden";
    }
    return this.repository.save(customer, total);
  }
}
