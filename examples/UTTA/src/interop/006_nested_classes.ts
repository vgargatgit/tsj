// UTTA Interop 006: Nested / Inner Classes
// Tests accessing Java static nested classes and constants

import { $new as newOuter, $instance$getName as getName, VERSION, MAX_SIZE, PI_APPROX } from "java:dev.utta.Outer";
import { $new as newConfig, $instance$getValue as getValue, $instance$describe as describeConfig } from "java:dev.utta.Outer$Config";
import { $new as newBuilder, $instance$setName as setName, $instance$setCount as setCount, $instance$build as build } from "java:dev.utta.Outer$Builder";

// 1. Construct outer class
const o = newOuter("test");
console.log("outer_new:" + (getName(o) === "test"));

// 2. Access static nested class
const cfg = newConfig(42);
console.log("nested_new:" + (getValue(cfg) === 42));

// 3. Nested class method
console.log("nested_method:" + (describeConfig(cfg) === "Config(v=42)"));

// 4. Builder pattern via nested class
const b = newBuilder();
setName(b, "hello");
setCount(b, 5);
const result = build(b);
console.log("builder:" + (result === "hello:5"));

// 5. Static final constants
console.log("const_str:" + (VERSION === "1.0.0"));
console.log("const_int:" + (MAX_SIZE === 1024));
console.log("const_dbl:" + (Math.abs(PI_APPROX - 3.14159) < 0.001));
