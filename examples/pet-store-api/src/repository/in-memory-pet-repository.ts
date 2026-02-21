export class InMemoryPetRepository {
  constructor() {
    this.pets = [
      {
        id: "pet-1",
        name: "Luna",
        species: "cat",
        age: 3,
        vaccinated: true
      },
      {
        id: "pet-2",
        name: "Milo",
        species: "dog",
        age: 5,
        vaccinated: false
      }
    ];
    this.nextId = 3;
  }

  list() {
    return this.pets.slice();
  }

  findById(id: string) {
    let index = 0;
    while (index < this.pets.length) {
      const current = this.pets.at(index);
      if (current.id === id) {
        return current;
      }
      index = index + 1;
    }
    throw "pet-not-found:" + id;
  }

  create(name: string, species: string, age: number, vaccinated: boolean) {
    const pet = {
      id: "pet-" + this.nextId,
      name: name,
      species: species,
      age: age,
      vaccinated: vaccinated
    };
    this.nextId = this.nextId + 1;
    this.pets.push(pet);
    return pet;
  }

  update(id: string, name: string, species: string, age: number, vaccinated: boolean) {
    const updated = {
      id: id,
      name: name,
      species: species,
      age: age,
      vaccinated: vaccinated
    };
    let index = 0;
    let found = false;
    const nextPets = [];
    while (index < this.pets.length) {
      const current = this.pets.at(index);
      if (current.id === id) {
        nextPets.push(updated);
        found = true;
      } else {
        nextPets.push(current);
      }
      index = index + 1;
    }
    if (found === false) {
      throw "pet-not-found:" + id;
    }
    this.pets = nextPets;
    return updated;
  }

  deleteById(id: string) {
    let index = 0;
    let found = false;
    const nextPets = [];
    while (index < this.pets.length) {
      const current = this.pets.at(index);
      if (current.id === id) {
        found = true;
      } else {
        nextPets.push(current);
      }
      index = index + 1;
    }
    this.pets = nextPets;
    return {
      deleted: found,
      id: id
    };
  }
}
