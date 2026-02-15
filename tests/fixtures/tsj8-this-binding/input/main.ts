const counter = {
  value: 1,
  inc() {
    this.value = this.value + 1;
    return this.value;
  },
  add: function(step: number) {
    this.value = this.value + step;
    return this.value;
  },
  makeAdder() {
    const apply = (step: number) => {
      this.value = this.value + step;
      return this.value;
    };
    return apply;
  }
};

const apply = counter.makeAdder();
console.log("method=" + counter.inc());
console.log("function=" + counter.add(5));
console.log("arrow=" + apply(4));
