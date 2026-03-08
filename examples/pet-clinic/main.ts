import { Owner } from "./src/domain/owner";
import { Pet } from "./src/domain/pet";
import "./src/repository/clinic-repository";
import "./src/service/clinic-service";
import "./src/web/pet-clinic-controller";

const owner = new Owner();
owner.id = "1";
owner.firstName = "George";
owner.lastName = "Franklin";

const pet = new Pet();
pet.id = "101";
pet.name = "Leo";
pet.type = "cat";
pet.birthDate = "2019-01-20";

console.log("tsj-pet-clinic-boot");
console.log("owner=" + owner.firstName);
console.log("pet=" + pet.name);
