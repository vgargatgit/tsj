// XTTA Builtins Torture: Map and Set

// 1. Map basic
const map = new Map<string, number>();
map.set("a", 1);
map.set("b", 2);
console.log("map_basic:" + (map.get("a") === 1 && map.size === 2));

// 2. Map has/delete
console.log("map_has:" + (map.has("a") === true && map.has("z") === false));
map.delete("a");
console.log("map_delete:" + (map.has("a") === false && map.size === 1));

// 3. Map from entries
const map2 = new Map<string, number>([["x", 10], ["y", 20]]);
console.log("map_entries:" + (map2.get("x") === 10 && map2.size === 2));

// 4. Map iteration
const map3 = new Map<string, number>([["a", 1], ["b", 2], ["c", 3]]);
let mapSum = 0;
map3.forEach((v, _k) => { mapSum += v; });
console.log("map_forEach:" + (mapSum === 6));

// 5. Map keys/values
const mapKeys: string[] = [];
map3.forEach((_v, k) => { mapKeys.push(k); });
console.log("map_keys:" + (mapKeys.length === 3));

// 6. Set basic
const set = new Set<number>();
set.add(1);
set.add(2);
set.add(2); // duplicate
console.log("set_basic:" + (set.size === 2));

// 7. Set has/delete
console.log("set_has:" + (set.has(1) === true && set.has(99) === false));
set.delete(1);
console.log("set_delete:" + (set.has(1) === false && set.size === 1));

// 8. Set from array
const set2 = new Set([1, 2, 3, 2, 1]);
console.log("set_dedup:" + (set2.size === 3));

// 9. Set iteration
const set3 = new Set([10, 20, 30]);
let setSum = 0;
set3.forEach(v => { setSum += v; });
console.log("set_forEach:" + (setSum === 60));

// 10. Set clear
set3.clear();
console.log("set_clear:" + (set3.size === 0));

// 11. Map clear
const map4 = new Map([["a", 1]]);
map4.clear();
console.log("map_clear:" + (map4.size === 0));

// 12. Map overwrite
const map5 = new Map<string, number>();
map5.set("key", 1);
map5.set("key", 2);
console.log("map_overwrite:" + (map5.get("key") === 2 && map5.size === 1));
