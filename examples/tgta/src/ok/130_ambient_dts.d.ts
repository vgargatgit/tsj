declare namespace AmbientNs {
  function pick(input: number): string;
}

declare module "ambient-pkg" {
  export const value: number;
  export function run(): void;
}

declare const ambientConst: string;
declare function ambientFn(value: string): number;

interface AmbientShape {
  readonly id: string;
  optional?: number;
}

declare global {
  interface Array<T> {
    tgtaTag?: string;
  }
}

export as namespace AmbientGlobal;
export {};
