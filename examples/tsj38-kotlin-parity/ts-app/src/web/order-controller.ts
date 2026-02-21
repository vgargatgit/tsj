import { InMemoryOrderRepository } from "../repository/in-memory-order-repository";
import { SecurityPolicy } from "../security/security-policy";
import { OrderService } from "../service/order-service";

@RestController
@RequestMapping("/api/orders")
export class OrderController {
  private readonly service: OrderService;

  constructor() {
    const repository = new InMemoryOrderRepository();
    const policy = new SecurityPolicy();
    this.service = new OrderService(repository, policy);
  }

  @GetMapping("/list")
  list(@RequestHeader("X-Role") role: string) {
    return this.service.list(role);
  }

  @PostMapping("/create")
  create(@RequestHeader("X-Role") role: string, @RequestBody payload: any) {
    return this.service.create(role, payload.customer, payload.total);
  }
}
