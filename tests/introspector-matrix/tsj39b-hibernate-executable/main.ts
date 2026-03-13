import { Column } from "java:jakarta.persistence.Column";
import { Entity } from "java:jakarta.persistence.Entity";
import { Id } from "java:jakarta.persistence.Id";
import { Table } from "java:jakarta.persistence.Table";

@Entity
@Table({ name: "people" })
class HibernateMatrixPerson {
  @Id
  id: string;

  @Column({ name: "display_name" })
  name: string;

  constructor() {
    this.id = "";
    this.name = "";
  }
}
