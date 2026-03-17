import { Operation } from "java:io.swagger.v3.oas.annotations.Operation";
import { Tag } from "java:io.swagger.v3.oas.annotations.tags.Tag";
import { GetMapping } from "java:org.springframework.web.bind.annotation.GetMapping";
import { PathVariable } from "java:org.springframework.web.bind.annotation.PathVariable";
import { PostMapping } from "java:org.springframework.web.bind.annotation.PostMapping";
import { RequestBody } from "java:org.springframework.web.bind.annotation.RequestBody";
import { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import { RequestParam } from "java:org.springframework.web.bind.annotation.RequestParam";
import { RestController } from "java:org.springframework.web.bind.annotation.RestController";

import { ClinicService } from "../service/clinic-service";
import { NewPetRequest } from "./new-pet-request";

@RestController
@RequestMapping("/api/petclinic")
@Tag({ name: "pet-clinic", description: "Owners and pets managed by the TSJ pet clinic sample." })
export class PetClinicController {
  service: ClinicService;

  constructor(service: ClinicService) {
    this.service = service;
  }

  @Operation({ summary: "List owners by last name." })
  @GetMapping("/owners")
  listOwners(@RequestParam("lastName") lastName: string) {
    return this.service.findOwners(lastName);
  }

  @Operation({ summary: "List pets belonging to an owner." })
  @GetMapping("/owners/{ownerId}/pets")
  petsByOwner(@PathVariable("ownerId") ownerId: string) {
    return this.service.findPets(ownerId);
  }

  @Operation({ summary: "Add a pet for an owner." })
  @PostMapping("/owners/{ownerId}/pets")
  addPet(@PathVariable("ownerId") ownerId: string, @RequestBody request: NewPetRequest) {
    return this.service.addPet(ownerId, request);
  }
}
