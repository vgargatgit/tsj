@Endpoint("health")
class HealthEndpoint {
  @ReadOperation
  health() {
    return "UP";
  }
}

@Endpoint("info")
class InfoEndpoint {
  @ReadOperation
  info() {
    return "tsj-actuator";
  }
}

@Endpoint("metrics")
class MetricsEndpoint {
  @ReadOperation
  summary() {
    return 42;
  }
}
