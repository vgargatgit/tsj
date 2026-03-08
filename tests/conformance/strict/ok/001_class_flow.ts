class Owner {
  id: string;
  name: string;

  constructor() {
    this.id = "7";
    this.name = "Ada";
  }

  label() {
    return this.id + ":" + this.name;
  }
}

const owner = new Owner();
console.log(owner.label());
