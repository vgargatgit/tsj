import {
  divider,
  defaultTitle
} from "./helpers.ts";

export function runFeaturePack() {
  console.log(divider(defaultTitle));

  const leftFalse = false
&&
"rhs-and";
  const leftTrue = true     ||
"rhs-or";
  const coalesced = null
??
"fallback";

     console.log(`logical:${leftFalse}|${leftTrue}|${coalesced}`);

  let score = 10;
  const captured = (score = 7);
  score +=
5;
  score    -= 2;
  score *= 3;
  score /= 2;
  score %= 5;

  let maybe = null;
  maybe ??= "filled";
  let orValue = false;
  orValue ||= "alt";
  let andValue = "seed";
  andValue &&= "next";
  console.log(`assign:${captured}|${score}|${maybe}|${orValue}|${andValue}`);

  const picked = score > 2 ? "GT2" : "LE2";
  console.log(`conditional:${picked}`);

  const holder = {
    value: 4,
    read: () => "ok"
  };
  const none = null;
  const maybeFn = undefined;
  console.log(`optional:${holder?.value}|${none?.value}|${holder.read?.()}|${maybeFn?.()}`);

  const who = "tsj";
  const count = 3;
  const templated = `hello ${who} #${count}`;
  console.log(`template:${templated}`);
}
