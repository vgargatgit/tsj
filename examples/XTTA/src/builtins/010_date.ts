// XTTA Builtins Torture: Date basics

// 1. Date.now
const now = Date.now();
console.log("date_now:" + (typeof now === "number" && now > 0));

// 2. new Date()
const d = new Date();
console.log("date_new:" + (d instanceof Date));

// 3. Date from timestamp
const d2 = new Date(0);
console.log("date_epoch:" + (d2.getTime() === 0));

// 4. Date.getFullYear
const d3 = new Date(2024, 0, 15);
console.log("date_year:" + (d3.getFullYear() === 2024));

// 5. Date.getMonth (0-indexed)
console.log("date_month:" + (d3.getMonth() === 0));

// 6. Date.getDate
console.log("date_day:" + (d3.getDate() === 15));

// 7. Date comparison via getTime
const d4 = new Date(2024, 0, 1);
const d5 = new Date(2024, 11, 31);
console.log("date_compare:" + (d4.getTime() < d5.getTime()));

// 8. Date.toISOString
const d6 = new Date(0);
const iso = d6.toISOString();
console.log("date_iso:" + (iso.includes("1970")));
