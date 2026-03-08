import type { Column } from "java:jakarta.persistence.Column";
import type { Entity } from "java:jakarta.persistence.Entity";
import type { Id } from "java:jakarta.persistence.Id";
import type { Table } from "java:jakarta.persistence.Table";

@Entity
@Table({ name: "owners" })
export class Owner {
  @Id
  @Column({ name: "id", nullable: false })
  id: string;

  @Column({ name: "first_name", nullable: false })
  firstName: string;

  @Column({ name: "last_name", nullable: false })
  lastName: string;

  constructor() {
    this.id = "";
    this.firstName = "";
    this.lastName = "";
  }
}
