class Threshold {
  value: number;

  constructor(value: number) {
    this.value = value;
  }

  normalize(limit: number): number {
    if (this.value > limit) {
      this.value = limit;
    } else {
      this.value = this.value + 1;
    }
    return this.value;
  }
}

const high = new Threshold(5).normalize(3);
const low = new Threshold(2).normalize(3);
console.log(high + ":" + low);
