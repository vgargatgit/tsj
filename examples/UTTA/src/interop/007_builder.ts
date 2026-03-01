// UTTA Interop 007: Builder Pattern (Fluent API)
// Tests method chaining with Java builder that returns this

import { $new as newQB, $instance$from as qbFrom, $instance$where as qbWhere, $instance$orderBy as qbOrderBy, $instance$limit as qbLimit, $instance$build as qbBuild } from "java:dev.utta.QueryBuilder";

// 1. Simple builder (no chaining - call step by step)
const q1b = newQB();
qbFrom(q1b, "users");
const q1 = qbBuild(q1b);
console.log("simple:" + (q1 === "SELECT * FROM users"));

// 2. Full builder (step by step since chaining returns Java this)
const q2b = newQB();
qbFrom(q2b, "orders");
qbWhere(q2b, "status = 'active'");
qbOrderBy(q2b, "created_at");
qbLimit(q2b, 10);
const q2 = qbBuild(q2b);
console.log("full:" + (q2 === "SELECT * FROM orders WHERE status = 'active' ORDER BY created_at LIMIT 10"));

// 3. Partial builder
const q3b = newQB();
qbFrom(q3b, "products");
qbLimit(q3b, 5);
const q3 = qbBuild(q3b);
console.log("partial:" + (q3 === "SELECT * FROM products LIMIT 5"));

// 4. Builder with where only
const q4b = newQB();
qbFrom(q4b, "logs");
qbWhere(q4b, "level = 'error'");
const q4 = qbBuild(q4b);
console.log("reuse:" + (q4.includes("logs") && q4.includes("error")));
