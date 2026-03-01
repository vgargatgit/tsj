// UTTA Interop 010: Cross-class Interop
// Tests using multiple Java classes together in one TS program

import { $new as newPoint, $instance$x as px, $instance$y as py, $instance$distanceTo as distanceTo, $instance$translate as translate } from "java:dev.utta.Point";
import { $new as newCircle, $instance$area as circleArea } from "java:dev.utta.Circle";
import { $new as newRect, $instance$area as rectArea } from "java:dev.utta.Rectangle";
import { valueOf } from "java:dev.utta.Color";
import { $instance$name as eName } from "java:dev.utta.Color";
import { $new as newQB, $instance$from as qbFrom, $instance$where as qbWhere, $instance$build as qbBuild } from "java:dev.utta.QueryBuilder";

// 1. Use Point and Circle together
const center = newPoint(0.0, 0.0);
const edge = newPoint(3.0, 4.0);
const radius = distanceTo(center, edge);
const circle = newCircle(radius);
console.log("cross_geom:" + (Math.abs(circleArea(circle) - 78.539) < 0.5));

// 2. Mix shapes
const c1 = newCircle(1.0);
const r1 = newRect(2.0, 3.0);
const totalArea = circleArea(c1) + rectArea(r1);
console.log("cross_shapes:" + (totalArea > 9.0));

// 3. Use enum with builder
const red = valueOf("RED");
const colorName = eName(red);
const qb = newQB();
qbFrom(qb, "products");
qbWhere(qb, "color = '" + colorName + "'");
const query = qbBuild(qb);
console.log("cross_build:" + (query.includes("RED")));

// 4. Point arithmetic
const p1 = newPoint(1.0, 2.0);
const p2 = translate(p1, 3.0, 4.0);
const p3 = translate(p2, -1.0, -1.0);
console.log("cross_chain:" + (px(p3) === 3.0 && py(p3) === 5.0));
