@RestController
@RequestMapping("/secure")
class SecurityMatrixController {
  @GetMapping("/public")
  publicEndpoint() {
    return "public";
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/admin")
  adminEndpoint() {
    return "admin";
  }

  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @GetMapping("/support")
  supportEndpoint() {
    return "support";
  }
}
