@RestController
@RequestMapping("/api")
export class HealthController {
  @GetMapping("/health")
  health() {
    return {
      service: "pet-store",
      status: "UP"
    };
  }
}

