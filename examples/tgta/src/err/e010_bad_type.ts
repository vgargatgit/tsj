type BrokenConditional<T> = T extends ? string : number;
type BrokenMapped<T> = { [K in keyof T as ]: T[K] };
type BrokenInfer = infer R;
const afterTypeErrors = 42;
