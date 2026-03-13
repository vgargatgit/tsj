import type { Service } from "java:org.springframework.stereotype.Service";
import type { Transactional } from "java:org.springframework.transaction.annotation.Transactional";
import type { PostMapping } from "java:org.springframework.web.bind.annotation.PostMapping";
import type { RequestBody } from "java:org.springframework.web.bind.annotation.RequestBody";
import type { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import type { RestController } from "java:org.springframework.web.bind.annotation.RestController";

class AuditPayload {
  id: string;
  message: string;

  constructor() {
    this.id = "audit-1";
    this.message = "saved";
  }
}

@Service
class AuditService {
  @Transactional
  save(payload: AuditPayload) {
    return payload.message;
  }
}

@RestController
@RequestMapping("/api/audit")
class AuditController {
  service: AuditService;

  constructor(service: AuditService) {
    this.service = service;
  }

  @PostMapping("/save")
  save(@RequestBody payload: AuditPayload) {
    return this.service.save(payload);
  }
}

console.log("tsj85-spring-aop-web-di");
