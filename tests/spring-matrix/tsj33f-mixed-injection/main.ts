import { Autowired } from "java:org.springframework.beans.factory.annotation.Autowired";
import { Qualifier } from "java:org.springframework.beans.factory.annotation.Qualifier";
import { Bean } from "java:org.springframework.context.annotation.Bean";
import { Configuration } from "java:org.springframework.context.annotation.Configuration";
import { Service } from "java:org.springframework.stereotype.Service";

@Configuration
class MixedConfig {
  @Bean
  clockBean() {
    return "clock";
  }

  @Bean
  metricsBean() {
    return "metrics";
  }

  @Bean
  pricingBean() {
    return "pricing";
  }
}

@Service
class MixedInjectionService {
  constructor(@Qualifier("clockBean") clock: any) {
  }

  @Autowired
  @Qualifier("metricsBean")
  metrics: any;

  pricing: any;

  @Autowired
  @Qualifier("pricingBean")
  setPricing(value: any) {
    this.pricing = value;
  }

  report() {
    return this.metrics + "|" + this.pricing;
  }
}
