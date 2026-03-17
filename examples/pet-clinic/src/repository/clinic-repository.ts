import type { EntityManager } from "java:jakarta.persistence.EntityManager";
import { randomUUID } from "java:java.util.UUID";
import { Repository } from "java:org.springframework.stereotype.Repository";

import { Owner } from "../domain/owner";
import { Pet } from "../domain/pet";

@Repository
export class ClinicRepository {
  entityManager: EntityManager;

  constructor(entityManager: EntityManager) {
    this.entityManager = entityManager;
  }

  findOwnersByLastName(lastName: string) {
    return this.entityManager
      .createQuery(
        "select o from Owner o where o.lastName like :lastName order by o.id",
        Owner
      )
      .setParameter("lastName", "%" + lastName + "%")
      .getResultList();
  }

  findPetsByOwner(ownerId: string) {
    return this.entityManager
      .createQuery(
        "select p from Pet p where p.ownerId = :ownerId order by p.id",
        Pet
      )
      .setParameter("ownerId", ownerId)
      .getResultList();
  }

  nextPetId() {
    return randomUUID().toString();
  }

  savePet(pet: Pet) {
    this.entityManager.persist(pet);
    return pet;
  }
}
