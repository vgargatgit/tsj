declare const declaredTop: string;
declare const maybeText: string | null;

if (maybeText) {
  console.log(maybeText);
} else {
  console.log(declaredTop);
}

switch (2) {
  case 0:
    console.log("zero");
    break;
  case 1:
    console.log("one");
  default:
    console.log("fallthrough");
    break;
}

outer: for (let i = 0; i < 3; i += 1) {
  for (let j = 0; j < 3; j += 1) {
    if (j === 1) {
      continue outer;
    }
    if (i === 2) {
      break outer;
    }
  }
}

for (const key in { a: 1, b: 2 }) {
  console.log(key);
}

for (const value of [1, 2, 3]) {
  console.log(value);
}

try {
  throw new Error("boom");
} catch (error) {
  console.log(error);
} finally {
  console.log("done");
}

try {
  throw "x";
} catch {
  console.log("caught");
}

function withStatementTarget(scope: Record<string, number>): Record<string, number> {
  with (scope) {
    value = 1;
  }
  debugger;
  return scope;
}

using statementResource = {
  [Symbol.dispose](): void {
    // noop
  },
};

void statementResource;
void withStatementTarget;
