import "./bootstrap.ts";
import { sumTo, makeAdder, double } from "./math.ts";
import { buildUser, PremiumAccount } from "./users.ts";
import { moduleReady, asyncOps, computeSeries } from "./async-work.ts";
import { runPromiseLab } from "./promise-lab.ts";

const adder = makeAdder(5);
const total = sumTo(5);
const boosted = adder(10);
const account = new PremiumAccount(4);
const payload = { label: "ok", count: 2 };
payload.count = payload.count + 1;

console.log("sync:ready=" + moduleReady);
console.log("sync:total=" + total);
console.log("sync:boosted=" + boosted);
console.log("sync:account=" + account.read() + ":" + account.bonus());

const user = buildUser("ada", double(boosted));
console.log("sync:user=" + user.tag());

console.log("sync:coerce=" + (1 == "1") + ":" + (1 === "1"));
const missing = { label: "x" };
console.log("sync:missing=" + missing.count);

const boostedAsync = await asyncOps.boost(10);
console.log("async:boost=" + boostedAsync);

const series = await computeSeries(5);
console.log("async:series=" + series);

const promiseLab = await runPromiseLab(series);
console.log("async:" + promiseLab);

console.log("sync:done");
