import type { EntityManager } from "java:jakarta.persistence.EntityManager";
import { createEntityManagerFactory } from "java:jakarta.persistence.Persistence";
import type { Repository } from "java:org.springframework.stereotype.Repository";

@Repository
export class ClinicRepository {
  entityManager: EntityManager;

  constructor() {
    this.entityManager = createEntityManagerFactory("petclinic").createEntityManager();
  }

  findOwnersByLastName(lastName: string) {
    return this.entityManager
      .createNativeQuery("select id, first_name, last_name from owners where lower(last_name) like lower(concat('%', ?1, '%')) order by id")
      .setParameter(1, lastName)
      .getResultList();
  }

  findPetsByOwner(ownerId: string) {
    return this.entityManager
      .createNativeQuery("select id, owner_id, name, type, birth_date from pets where owner_id = ?1 order by id")
      .setParameter(1, ownerId)
      .getResultList();
  }

  addPet(ownerId: string, name: string, type: string, birthDate: string) {
    const tx = this.entityManager.getTransaction();
    tx.begin();

    const nextId = this.entityManager
      .createNativeQuery("select coalesce(max(id), 100) + 1 from pets")
      .getSingleResult();

    this.entityManager
      .createNativeQuery("insert into pets(id, owner_id, name, type, birth_date) values (?1, ?2, ?3, ?4, ?5)")
      .setParameter(1, nextId)
      .setParameter(2, ownerId)
      .setParameter(3, name)
      .setParameter(4, type)
      .setParameter(5, birthDate)
      .executeUpdate();

    tx.commit();

    return this.entityManager
      .createNativeQuery("select id, owner_id, name, type, birth_date from pets where id = ?1")
      .setParameter(1, nextId)
      .getSingleResult();
  }
}
