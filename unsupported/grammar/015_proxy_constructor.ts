const target = { value: 9 };
const proxy = new Proxy(target, {});

console.log(proxy.value);
