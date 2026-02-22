interface CallableShape {
  (x: number): string;
  new (x: number): CallableShape;
  [index: number]: string;
  readonly label?: string;
}

interface Mergeable {
  a: string;
}

interface Mergeable {
  b: number;
}

interface A {
  aa: string;
}

interface B {
  bb: number;
}

interface Combined extends A, B {
  readonly id: string;
  optional?: boolean;
}

type InterfaceUse = CallableShape | Mergeable | Combined;
void (0 as unknown as InterfaceUse);
