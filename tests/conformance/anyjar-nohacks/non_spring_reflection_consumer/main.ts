import type { RuntimeMark } from "java:sample.anno.RuntimeMark";

@RuntimeMark
class ReflectedEntity {
  id: string;

  constructor() {
    this.id = "owner-1";
  }
}

console.log("tsj85-reflection-consumer");
