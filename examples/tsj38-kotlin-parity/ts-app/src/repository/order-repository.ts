import type { Order } from "../domain/order";

export interface OrderRepository {
  list(): Order[];
  save(customer: string, total: number): Order;
}
