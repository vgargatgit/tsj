@Endpoint("health")
class HealthEndpoint {
  @WriteOperation
  update() {
    return "ok";
  }
}
