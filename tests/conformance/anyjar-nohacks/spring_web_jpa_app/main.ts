import type { Entity } from "java:jakarta.persistence.Entity";
import type { Repository } from "java:org.springframework.stereotype.Repository";
import type { Service } from "java:org.springframework.stereotype.Service";
import type { GetMapping } from "java:org.springframework.web.bind.annotation.GetMapping";
import type { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import type { RequestParam } from "java:org.springframework.web.bind.annotation.RequestParam";
import type { RestController } from "java:org.springframework.web.bind.annotation.RestController";

@Entity
class Owner {
  id: string;
  lastName: string;

  constructor() {
    this.id = "owner-1";
    this.lastName = "Simpson";
  }
}

@Repository
class OwnerRepository {
  describeOwners(lastName: string) {
    return "owners:" + lastName;
  }
}

@Service
class ClinicService {
  repository: OwnerRepository;

  constructor(repository: OwnerRepository) {
    this.repository = repository;
  }

  findOwners(lastName: string) {
    return this.repository.describeOwners(lastName);
  }
}

@RestController
@RequestMapping("/api/owners")
class ClinicController {
  service: ClinicService;

  constructor(service: ClinicService) {
    this.service = service;
  }

  @GetMapping("/")
  list(@RequestParam("lastName") lastName: string) {
    return this.service.findOwners(lastName);
  }
}

console.log("tsj85-spring-web-jpa");
