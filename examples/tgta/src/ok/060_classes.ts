interface Runnable {
  run(): void;
}

abstract class BaseClass {
  protected count = 0;

  abstract run(this: this): void;
}

class DerivedClass extends BaseClass implements Runnable {
  public ready!: boolean;
  private readonly label: string;
  protected value = 1;
  static total = 0;
  #secret = 41;

  constructor(public x: number, label = "derived") {
    super();
    this.label = label;
  }

  get size(): number {
    return this.#secret;
  }

  set size(next: number) {
    this.#secret = next;
  }

  #touch(): void {
    this.#secret += 1;
  }

  override run(this: this): void {
    this.#touch();
    this.count += this.x;
  }

  static {
    this.total += 1;
  }
}

const ClassExpr = class LocalExpr {
  value = 0;

  method(this: LocalExpr): number {
    return this.value;
  }
};

void [DerivedClass, ClassExpr];
