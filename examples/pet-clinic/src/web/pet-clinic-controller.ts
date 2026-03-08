import type { Qualifier } from "java:org.springframework.beans.factory.annotation.Qualifier";
import type { GetMapping } from "java:org.springframework.web.bind.annotation.GetMapping";
import type { PathVariable } from "java:org.springframework.web.bind.annotation.PathVariable";
import type { PostMapping } from "java:org.springframework.web.bind.annotation.PostMapping";
import type { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import type { RequestParam } from "java:org.springframework.web.bind.annotation.RequestParam";
import type { RestController } from "java:org.springframework.web.bind.annotation.RestController";

type ClinicServicePort = {
  findOwners(lastName: string): OwnerRow[];
  findPets(ownerId: string): PetRow[];
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

@RestController
@RequestMapping("/api/petclinic")
export class PetClinicController {
  service: ClinicServicePort;

  constructor(@Qualifier("clinicServiceTsjComponent") service: ClinicServicePort) {
    this.service = service;
  }

  @GetMapping("/owners")
  listOwners(@RequestParam("lastName") lastName: string) {
    return this.service.findOwners(lastName);
  }

  @GetMapping("/owners/{ownerId}/pets")
  petsByOwner(@PathVariable("ownerId") ownerId: string) {
    return this.service.findPets(ownerId);
  }

  @PostMapping("/owners/{ownerId}/pets")
  addPet(@PathVariable("ownerId") ownerId: string, @RequestParam("name") name: string, @RequestParam("type") type: string, @RequestParam("birthDate") birthDate: string) {
    return this.service.addPet(ownerId, name, type, birthDate);
  }
}
