// XTTA Interop Torture: Deep Generics and Collections
import { deepGenericMap, wrapInList, pair } from "java:dev.xtta.complex.TortureLib";

// 1. Wrap in list
const list = wrapInList("hello");
console.log("wrap_list:" + (list !== null && list !== undefined));

// 2. Deep generic map
const dmap = deepGenericMap();
console.log("deep_map:" + (dmap !== null && dmap !== undefined));

// 3. Generic pair
const p = pair("key", 42);
console.log("pair:" + (p !== null && p !== undefined));
