function dec(..._args: unknown[]) {
  return function decoratorTarget(...inner: unknown[]) {
    return inner[0];
  };
}

@dec()
class DecoratedClass {
  @dec()
  accessor value: number = 1;

  @dec()
  method(input: number): number {
    return input + this.value;
  }
}

void DecoratedClass;
