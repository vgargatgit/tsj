import type { Order } from "../domain/order";
import type { OrderRepository } from "./order-repository";

export class InMemoryOrderRepository implements OrderRepository {
  private readonly orders: Order[];
  private nextId: number;

  constructor() {
    this.orders = [];
    this.nextId = 1;
  }

  list(): Order[] {
    return this.orders.slice();
  }

  save(customer: string, total: number): Order {
    const order: Order = {
      id: "order-" + this.nextId,
      customer: customer,
      total: total
    };
    this.nextId = this.nextId + 1;
    this.orders.push(order);
    return order;
  }
}
