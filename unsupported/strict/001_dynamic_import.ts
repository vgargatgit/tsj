// EXPECT_CODE: TSJ-STRICT-UNSUPPORTED
// EXPECT_FEATURE_ID: TSJ-STRICT-DYNAMIC-IMPORT

const moduleRef = import("./_dep.ts");
console.log(moduleRef);
