import { callB, pingA } from "./a.ts";
console.log("cycle=" + pingA() + ":" + callB());
