package dev.tsj.generated.web;

@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/api")
public final class HealthControllerTsjController {
    public HealthControllerTsjController() {
    }

    @org.springframework.web.bind.annotation.GetMapping("/health")
    public Object health() {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("HealthController", "health");
    }

}
