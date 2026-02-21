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
