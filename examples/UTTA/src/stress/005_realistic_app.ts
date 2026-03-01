// UTTA Stress 005: Complex Multi-Pattern Program
// Tests a realistic program combining many features

// --- Domain types ---
interface Product {
  id: number;
  name: string;
  price: number;
  category: string;
  inStock: boolean;
}

// --- Repository pattern ---
class ProductRepo {
  private products: Product[] = [];
  private nextId = 1;

  add(name: string, price: number, category: string): Product {
    const p: Product = { id: this.nextId++, name, price, category, inStock: true };
    this.products.push(p);
    return p;
  }

  findById(id: number): Product | undefined {
    return this.products.find(p => p.id === id);
  }

  findByCategory(cat: string): Product[] {
    return this.products.filter(p => p.category === cat);
  }

  updateStock(id: number, inStock: boolean): boolean {
    const p = this.findById(id);
    if (!p) return false;
    p.inStock = inStock;
    return true;
  }

  totalValue(): number {
    return this.products
      .filter(p => p.inStock)
      .reduce((sum, p) => sum + p.price, 0);
  }

  count(): number { return this.products.length; }
}

// --- Service layer ---
class OrderService {
  private orders: { productId: number; quantity: number; total: number }[] = [];

  constructor(private repo: ProductRepo) {}

  placeOrder(productId: number, quantity: number): string {
    const product = this.repo.findById(productId);
    if (!product) return "NOT_FOUND";
    if (!product.inStock) return "OUT_OF_STOCK";
    const total = product.price * quantity;
    this.orders.push({ productId, quantity, total });
    return "OK:" + total;
  }

  totalRevenue(): number {
    return this.orders.reduce((sum, o) => sum + o.total, 0);
  }

  orderCount(): number { return this.orders.length; }
}

// --- Execute ---
const repo = new ProductRepo();
repo.add("Widget", 9.99, "hardware");
repo.add("Gadget", 24.99, "electronics");
repo.add("Doohickey", 4.99, "hardware");
repo.add("Thingamajig", 49.99, "electronics");
repo.add("Whatsit", 14.99, "misc");

console.log("repo_count:" + (repo.count() === 5));

// Category filter
const hw = repo.findByCategory("hardware");
console.log("category:" + (hw.length === 2));

// Find by ID
const g = repo.findById(2);
console.log("find_id:" + (g !== undefined && g !== null && g.name === "Gadget"));

// Order service
const svc = new OrderService(repo);
console.log("order_ok:" + (svc.placeOrder(1, 3).startsWith("OK:")));
console.log("order_ok2:" + (svc.placeOrder(2, 1).startsWith("OK:")));

// Out of stock
repo.updateStock(3, false);
console.log("oos:" + (svc.placeOrder(3, 1) === "OUT_OF_STOCK"));

// Not found
console.log("nf:" + (svc.placeOrder(99, 1) === "NOT_FOUND"));

// Revenue
console.log("revenue:" + (svc.totalRevenue() > 50));

// Total value (in-stock only)
console.log("value:" + (repo.totalValue() > 90));

// Order count
console.log("orders:" + (svc.orderCount() === 2));
