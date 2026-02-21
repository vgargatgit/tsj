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
