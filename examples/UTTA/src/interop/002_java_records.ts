// UTTA Interop 002: Java Records
// Tests Java record class interop

import { $new as newPoint, $instance$x as px, $instance$y as py, $instance$distanceTo as distanceTo, $instance$translate as translate, $instance$toString as ptStr, origin } from "java:dev.utta.Point";

// 1. Record construction
const p = newPoint(3.0, 4.0);
console.log("record_new:" + (p !== null));

// 2. Record accessor methods
const x = px(p);
const y = py(p);
console.log("record_access:" + (x === 3.0 && y === 4.0));

// 3. Record instance method
const o = newPoint(0.0, 0.0);
const dist = distanceTo(p, o);
console.log("record_method:" + (Math.abs(dist - 5.0) < 0.001));

// 4. Record static factory
const orig = origin();
console.log("record_static:" + (px(orig) === 0.0 && py(orig) === 0.0));

// 5. Record method returning new record
const moved = translate(p, 1.0, 1.0);
console.log("record_chain:" + (px(moved) === 4.0 && py(moved) === 5.0));

// 6. Record toString
const str = ptStr(p);
console.log("record_str:" + (str.includes("3.0") && str.includes("4.0")));
