// UTTA Grammar 005: Bitwise Operators
// Tests all bitwise operations

// 1. Bitwise AND
console.log("and:" + ((0b1100 & 0b1010) === 0b1000));

// 2. Bitwise OR
console.log("or:" + ((0b1100 | 0b1010) === 0b1110));

// 3. Bitwise XOR
console.log("xor:" + ((0b1100 ^ 0b1010) === 0b0110));

// 4. Bitwise NOT
console.log("not:" + ((~0) === -1));

// 5. Left shift
console.log("shl:" + ((1 << 4) === 16));

// 6. Signed right shift
console.log("shr:" + ((-16 >> 2) === -4));

// 7. Unsigned right shift
console.log("ushr:" + ((-1 >>> 0) === 4294967295));

// 8. Compound bitwise assignment (&=)
let a = 0xFF;
a &= 0x0F;
console.log("and_eq:" + (a === 0x0F));

// 9. Compound bitwise assignment (|=)
let b = 0x00;
b |= 0xFF;
console.log("or_eq:" + (b === 0xFF));

// 10. Compound bitwise assignment (^=)
let c = 0xFF;
c ^= 0x0F;
console.log("xor_eq:" + (c === 0xF0));

// 11. Compound shift assignment (<<=)
let d = 1;
d <<= 8;
console.log("shl_eq:" + (d === 256));

// 12. Compound shift assignment (>>=)
let e = 256;
e >>= 4;
console.log("shr_eq:" + (e === 16));
