@RestController
@RequestMapping("/secure")
class SecurityUnsupportedController {
  @PreAuthorize("hasAuthority('SCOPE_read')")
  @GetMapping("/authority")
  authorityEndpoint() {
    return "authority";
  }
}
