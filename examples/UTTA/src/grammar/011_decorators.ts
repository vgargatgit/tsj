// UTTA Grammar 011: Decorators
// Tests decorator syntax (TC39 Stage 3 / TS 5.x)

// 1. Class decorator
function sealed(target: any) {
  Object.seal(target);
  Object.seal(target.prototype);
}

@sealed
class Frozen {
  x = 1;
  method() { return this.x; }
}
const f = new Frozen();
console.log("class_dec:" + (f.method() === 1));

// 2. Method decorator
function log(_target: any, _key: string, descriptor: PropertyDescriptor) {
  const orig = descriptor.value;
  descriptor.value = function (...args: any[]) {
    return "logged:" + orig.apply(this, args);
  };
  return descriptor;
}

class Svc {
  @log
  hello(name: string) { return name; }
}
const svc = new Svc();
console.log("method_dec:" + (svc.hello("world") === "logged:world"));

// 3. Property decorator
function defaultVal(val: any) {
  return function (_target: any, key: string) {
    const symbol = Symbol(key);
    Object.defineProperty(_target, key, {
      get() { return this[symbol] ?? val; },
      set(v: any) { this[symbol] = v; }
    });
  };
}

class Config {
  @defaultVal(3000)
  port!: number;
  @defaultVal("localhost")
  host!: string;
}
const cfg = new Config();
console.log("prop_dec:" + (cfg.port === 3000 && cfg.host === "localhost"));

// 4. Multiple decorators (composition)
function addTag(tag: string) {
  return function (target: any) {
    target.prototype.tags = (target.prototype.tags || []).concat(tag);
  };
}

@addTag("a")
@addTag("b")
class Tagged {}
const t = new Tagged() as any;
console.log("multi_dec:" + (t.tags.length === 2));

// 5. Decorator on static method
class StaticSvc {
  @log
  static greet() { return "hello"; }
}
console.log("static_dec:" + (StaticSvc.greet() === "logged:hello"));
