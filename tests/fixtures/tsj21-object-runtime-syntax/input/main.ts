const proto = { shared: "base", kind: "proto" };
const payload = { own: "x", shared: "local" };

console.log("del1=" + delete payload.own);
console.log("own=" + payload.own);

payload.__proto__ = proto;
console.log("del2=" + delete payload.shared);
console.log("shared=" + payload.shared);
console.log("del3=" + delete payload.missing);

const target = {};
const returned = Object.setPrototypeOf(target, proto);
console.log("ret=" + (returned === target));
console.log("kind=" + target.kind);

const nextProto = { kind: "next" };
target.__proto__ = nextProto;
console.log("kind2=" + target.kind);

const cleared = Object.setPrototypeOf(target, null);
console.log("cleared=" + (cleared === target));
console.log("kind3=" + target.kind);
