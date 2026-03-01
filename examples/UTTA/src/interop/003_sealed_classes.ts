// UTTA Interop 003: Java Sealed Classes
// Tests sealed interface hierarchy interop

import { $new as newCircle, $instance$area as circleArea, $instance$describe as circleDesc, $instance$getRadius as getRadius } from "java:dev.utta.Circle";
import { $new as newRect, $instance$area as rectArea, $instance$describe as rectDesc, $instance$getWidth as getWidth, $instance$getHeight as getHeight } from "java:dev.utta.Rectangle";

// 1. Construct sealed subclass
const c = newCircle(5.0);
console.log("sealed_new:" + (c !== null));

// 2. Call interface method on sealed implementation
const area = circleArea(c);
console.log("sealed_area:" + (Math.abs(area - 78.539) < 0.1));

// 3. Call describe() method
const desc = circleDesc(c);
console.log("sealed_desc:" + (desc.includes("Circle")));

// 4. Another sealed subclass
const r = newRect(3.0, 4.0);
console.log("rect_area:" + (rectArea(r) === 12.0));

// 5. Access specific subclass method
const radius = getRadius(c);
console.log("subclass_method:" + (radius === 5.0));

// 6. Rectangle specific methods
console.log("rect_dims:" + (getWidth(r) === 3.0 && getHeight(r) === 4.0));
