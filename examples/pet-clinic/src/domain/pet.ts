import type { Column } from "java:jakarta.persistence.Column";
import type { Entity } from "java:jakarta.persistence.Entity";
import type { Id } from "java:jakarta.persistence.Id";
import type { JoinColumn } from "java:jakarta.persistence.JoinColumn";
import type { ManyToOne } from "java:jakarta.persistence.ManyToOne";
import type { Table } from "java:jakarta.persistence.Table";

@Entity
@Table({ name: "pets" })
export class Pet {
  @Id
  @Column({ name: "id", nullable: false })
  id: string;

  @ManyToOne
  @JoinColumn({ name: "owner_id", nullable: false })
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
