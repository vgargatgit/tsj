const valueOfObject = {
  valueOf() {
    return 7;
  }
};
console.log("eq1=" + (valueOfObject == 7));

const toStringObject = {
  valueOf() {
    return {};
  },
  toString() {
    return "8";
  }
};
console.log("eq2=" + (toStringObject == 8));

const boolObject = {
  valueOf() {
    return 1;
  }
};
console.log("eq3=" + (boolObject == true));

const plain = {};
console.log("eq4=" + (plain == "[object Object]"));
console.log("eq5=" + (plain == null));

class Box {
  value: number;
  constructor(value: number) {
    this.value = value;
  }
  valueOf() {
    return this.value;
  }
}
const box = new Box(9);
console.log("eq6=" + (box == 9));

const nonCallableValueOf = {
  valueOf: 3,
  toString() {
    return "11";
  }
};
console.log("eq7=" + (nonCallableValueOf == 11));
