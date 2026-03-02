async function run() {
  const moduleValue = await import("./_support_module.ts");
  console.log(moduleValue.value);
}

run();
