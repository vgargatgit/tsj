// UTTA Grammar 004: Numeric Separators & Exotic Literals
// Tests numeric separator syntax and binary/octal/hex literals

// 1. Numeric separators in decimal
const million = 1_000_000;
console.log("sep_dec:" + (million === 1000000));

// 2. Numeric separators in hex
const hexVal = 0xFF_FF;
console.log("sep_hex:" + (hexVal === 65535));

// 3. Numeric separators in binary
const binVal = 0b1010_0001;
console.log("sep_bin:" + (binVal === 161));

// 4. Numeric separators in octal
const octVal = 0o77_77;
console.log("sep_oct:" + (octVal === 4095));

// 5. Numeric separators in float
const pi = 3.141_592_653;
console.log("sep_float:" + (Math.abs(pi - 3.141592653) < 0.0001));

// 6. Hex literal
const hex = 0xDEAD;
console.log("hex:" + (hex === 57005));

// 7. Binary literal
const bin = 0b11111111;
console.log("bin:" + (bin === 255));

// 8. Octal literal
const oct = 0o777;
console.log("oct:" + (oct === 511));

// 9. Exponent notation
const exp = 1.5e3;
console.log("exp:" + (exp === 1500));

// 10. Negative exponent
const small = 1.5e-3;
console.log("neg_exp:" + (Math.abs(small - 0.0015) < 0.0001));
