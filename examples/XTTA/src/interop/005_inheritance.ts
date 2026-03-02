// XTTA Interop Torture: Inheritance, Abstract, Default Methods
import { $new as newDog, $instance$describe as describeDog, $instance$sound as soundDog } from "java:dev.xtta.complex.Dog";
import { $new as newCat, $instance$describe as describeCat } from "java:dev.xtta.complex.Cat";
import { $new as newPerson, $instance$greet as greetPerson, $instance$describe as describePerson, $instance$summary as summaryPerson } from "java:dev.xtta.complex.Person";

// 1. Abstract class via concrete subclass
const dog = newDog();
const dogSound = soundDog(dog);
console.log("abstract_sound:" + (dogSound === "Woof"));

// 2. Abstract method dispatch
const dogDesc = describeDog(dog);
console.log("abstract_describe:" + (dogDesc === "Dog says Woof"));

// 3. Second subclass
const cat = newCat();
const catDesc = describeCat(cat);
console.log("cat_describe:" + (catDesc === "Cat says Meow"));

// 4. Default interface method
const person = newPerson("Alice", 30);
const greeting = greetPerson(person);
console.log("default_method:" + (greeting === "Hello, Alice!"));

// 5. Multiple interface implementation
const desc = describePerson(person);
console.log("multi_iface:" + (desc === "Alice (age 30)"));

// 6. Default method from second interface
const summary = summaryPerson(person);
console.log("summary_default:" + (summary === "Summary: Alice (age 30)"));
