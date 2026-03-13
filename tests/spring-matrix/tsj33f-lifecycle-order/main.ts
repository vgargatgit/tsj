import { PostConstruct } from "java:jakarta.annotation.PostConstruct";
import { PreDestroy } from "java:jakarta.annotation.PreDestroy";
import { Service } from "java:org.springframework.stereotype.Service";

@Service
class FirstLifecycleService {
  @PostConstruct
  initFirst() {
    return "first-init";
  }

  @PreDestroy
  shutdownFirst() {
    return "first-shutdown";
  }
}

@Service
class SecondLifecycleService {
  @PostConstruct
  initSecond() {
    return "second-init";
  }

  @PreDestroy
  shutdownSecond() {
    return "second-shutdown";
  }
}
