// UTTA Grammar 006: Proxy and Reflect
// Tests Proxy traps and Reflect API

// 1. Basic Proxy get trap
const target = { x: 1, y: 2, z: 3 };
const handler = {
  get(obj: any, prop: string) {
    return prop in obj ? obj[prop] : -1;
  }
};
const p = new Proxy(target, handler);
console.log("proxy_get:" + (p.x === 1));
console.log("proxy_miss:" + (p.w === -1));

// 2. Proxy set trap
const setLog: string[] = [];
const setter = new Proxy({} as any, {
  set(obj: any, prop: string, val: any) {
    setLog.push(prop);
    obj[prop] = val;
    return true;
  }
});
setter.a = 1;
setter.b = 2;
console.log("proxy_set:" + (setLog.length === 2 && setLog[0] === "a"));

// 3. Proxy has trap
const hasTrap = new Proxy({ secret: true } as any, {
  has(obj: any, prop: string) {
    return prop !== "secret" && prop in obj;
  }
});
console.log("proxy_has:" + (!("secret" in hasTrap)));

// 4. Reflect.ownKeys
const keys = Reflect.ownKeys({ a: 1, b: 2, c: 3 });
console.log("reflect_keys:" + (keys.length === 3));

// 5. Reflect.has
console.log("reflect_has:" + (Reflect.has({ x: 1 }, "x") === true));

// 6. Reflect.get / Reflect.set
const rObj: any = { val: 10 };
console.log("reflect_get:" + (Reflect.get(rObj, "val") === 10));
Reflect.set(rObj, "val", 20);
console.log("reflect_set:" + (rObj.val === 20));

// 7. Revocable proxy
const { proxy, revoke } = Proxy.revocable({ data: 42 }, {});
console.log("revocable:" + (proxy.data === 42));
