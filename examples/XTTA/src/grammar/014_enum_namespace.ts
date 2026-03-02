// XTTA Grammar Torture: Enum and Namespace
// Tests string enums, numeric enums, const enums, computed enums, namespaces

// 1. Numeric enum
enum Direction { Up, Down, Left, Right }
console.log("num_enum:" + (Direction.Up === 0 && Direction.Right === 3));

// 2. String enum
enum Color { Red = "RED", Green = "GREEN", Blue = "BLUE" }
console.log("str_enum:" + (Color.Red === "RED"));

// 3. Heterogeneous enum (mixed)
enum Mixed { No = 0, Yes = "YES" }
console.log("mixed_enum:" + (Mixed.No === 0 && Mixed.Yes === "YES"));

// 4. Const enum
const enum Flags { None = 0, Read = 1, Write = 2, Execute = 4 }
const perms = Flags.Read | Flags.Write;
console.log("const_enum:" + (perms === 3));

// 5. Enum with custom values
enum Http { Ok = 200, NotFound = 404, Error = 500 }
console.log("custom_enum:" + (Http.NotFound === 404));

// 6. Namespace
namespace MathOps {
  export function add(a: number, b: number) { return a + b; }
  export function mul(a: number, b: number) { return a * b; }
}
console.log("namespace:" + (MathOps.add(2, 3) === 5 && MathOps.mul(2, 3) === 6));

// 7. Nested namespace
namespace Outer {
  export namespace Inner {
    export const VALUE = 42;
  }
}
console.log("nested_ns:" + (Outer.Inner.VALUE === 42));

// 8. Enum reverse mapping (numeric only)
console.log("enum_reverse:" + (Direction[0] === "Up"));

// 9. Enum in switch
function dirName(d: Direction): string {
  switch (d) {
    case Direction.Up: return "up";
    case Direction.Down: return "down";
    default: return "other";
  }
}
console.log("enum_switch:" + (dirName(Direction.Up) === "up"));
