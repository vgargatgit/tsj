import { PetService } from "../service/pet-service";

@RestController
@RequestMapping("/api/pets")
export class PetController {
  constructor() {
    this.service = new PetService();
  }

  @GetMapping("/list")
  list() {
    return this.service.listPets();
  }

  @GetMapping("/get")
  getById(id: string) {
    return this.service.getPet(id);
  }

  @PostMapping("/create")
  create(name: string, species: string, age: number, vaccinated: boolean) {
    return this.service.createPet(name, species, age, vaccinated);
  }

  @PutMapping("/update")
  update(id: string, name: string, species: string, age: number, vaccinated: boolean) {
    return this.service.changePet(id, name, species, age, vaccinated);
  }

  @DeleteMapping("/delete")
  remove(id: string) {
    return this.service.deletePet(id);
  }
}
