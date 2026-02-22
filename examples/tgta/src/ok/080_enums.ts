enum NumericEnum {
  First,
  Second = 4,
  Third,
}

enum StringEnum {
  Up = "UP",
  Down = "DOWN",
}

enum MixedEnum {
  No = 0,
  Yes = "YES",
}

const enum Flags {
  None = 0,
  Read = 1,
  Write = 2,
}

const maskValue = Flags.Read | Flags.Write;
void [NumericEnum, StringEnum, MixedEnum, maskValue];
