// EXPECT_CODE: TSJ-STRICT-UNSUPPORTED
// EXPECT_FEATURE_ID: TSJ-STRICT-UNCHECKED-ANY-MEMBER-INVOKE

function invokeDynamic(target: any) {
  return target.run();
}

console.log(invokeDynamic({ run: () => 1 }));
