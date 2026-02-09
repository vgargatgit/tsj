export class Account {
  value: number;

  constructor(seed: number) {
    this.value = seed;
  }

  read() {
    return this.value;
  }
}

export class PremiumAccount extends Account {
  constructor(seed: number) {
    super(seed + 2);
  }

  bonus() {
    return this.value * 2;
  }
}

export class User {
  name: string;
  id: number;

  constructor(name: string, id: number) {
    this.name = name;
    this.id = id;
  }

  tag() {
    return this.name + "#" + this.id;
  }
}

export function buildUser(name: string, id: number) {
  return new User(name, id);
}
