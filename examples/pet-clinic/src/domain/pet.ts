import { Column } from "java:jakarta.persistence.Column";
import { Entity } from "java:jakarta.persistence.Entity";
import { Id } from "java:jakarta.persistence.Id";
import { Table } from "java:jakarta.persistence.Table";

@Entity
@Table({ name: "pets" })
export class Pet {
  @Id
  @Column({ name: "id", nullable: false })
  id: string;

  @Column({ name: "owner_id", nullable: false })
  ownerId: string;

  @Column({ name: "name", nullable: false })
  name: string;

  @Column({ name: "type", nullable: false })
  type: string;

  @Column({ name: "birth_date", nullable: false })
  birthDate: string;

  constructor() {
    this.id = "";
    this.ownerId = "";
    this.name = "";
    this.type = "";
    this.birthDate = "";
  }
}
