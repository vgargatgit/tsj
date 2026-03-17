import { Transactional } from "java:org.springframework.transaction.annotation.Transactional";
import { Service } from "java:org.springframework.stereotype.Service";

import { Pet } from "../domain/pet";
import { ClinicRepository } from "../repository/clinic-repository";
import { NewPetRequest } from "../web/new-pet-request";

@Service
export class ClinicService {
  repository: ClinicRepository;

  constructor(repository: ClinicRepository) {
    this.repository = repository;
  }

  findOwners(lastName: string) {
    return this.repository.findOwnersByLastName(lastName);
  }

  findPets(ownerId: string) {
    return this.repository.findPetsByOwner(ownerId);
  }

  @Transactional
  addPet(ownerId: string, request: NewPetRequest) {
    const pet = new Pet();
    pet.id = this.repository.nextPetId();
    pet.ownerId = ownerId;
    pet.name = request.name;
    pet.type = request.type;
    pet.birthDate = request.birthDate;
    return this.repository.savePet(pet);
  }
}
