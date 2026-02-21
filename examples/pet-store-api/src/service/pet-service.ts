import { InMemoryPetRepository } from "../repository/in-memory-pet-repository";

export class PetService {
  constructor() {
    this.repository = new InMemoryPetRepository();
  }

  listPets() {
    return this.repository.list();
  }

  getPet(id: string) {
    return this.repository.findById(id);
  }

  createPet(name: string, species: string, age: number, vaccinated: boolean) {
    this.validateRequest(name, species, age, vaccinated);
    return this.repository.create(name, species, age, vaccinated);
  }

  changePet(id: string, name: string, species: string, age: number, vaccinated: boolean) {
    this.validateRequest(name, species, age, vaccinated);
    return this.repository.update(id, name, species, age, vaccinated);
  }

  deletePet(id: string) {
    return this.repository.deleteById(id);
  }

  validateRequest(name: string, species: string, age: number, vaccinated: boolean) {
    if (name == null) {
      throw "invalid-pet-name";
    }
    if (name === "") {
      throw "invalid-pet-name";
    }
    if (species == null) {
      throw "invalid-pet-species";
    }
    if (species === "") {
      throw "invalid-pet-species";
    }
    if (age < 0) {
      throw "invalid-pet-age";
    }
    if (vaccinated !== true) {
      if (vaccinated !== false) {
        throw "invalid-vaccinated-flag";
      }
    }
  }
}
