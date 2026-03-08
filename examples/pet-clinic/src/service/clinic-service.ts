import type { Qualifier } from "java:org.springframework.beans.factory.annotation.Qualifier";
import type { Service } from "java:org.springframework.stereotype.Service";

type ClinicRepositoryPort = {
  findOwnersByLastName(lastName: string): OwnerRow[];
  findPetsByOwner(ownerId: string): PetRow[];
  addPet(ownerId: string, name: string, type: string, birthDate: string): PetRow;
};

type OwnerRow = {
  id: string;
  firstName: string;
  lastName: string;
};

type PetRow = {
  id: number;
  ownerId: string;
  name: string;
  type: string;
  birthDate: string;
};

@Service
export class ClinicService {
  repository: ClinicRepositoryPort;

  constructor(@Qualifier("clinicRepositoryTsjComponent") repository: ClinicRepositoryPort) {
    this.repository = repository;
  }

  findOwners(lastName: string) {
    return this.repository.findOwnersByLastName(lastName);
  }

  findPets(ownerId: string) {
    return this.repository.findPetsByOwner(ownerId);
  }

  addPet(ownerId: string, name: string, type: string, birthDate: string) {
    return this.repository.addPet(ownerId, name, type, birthDate);
  }
}
