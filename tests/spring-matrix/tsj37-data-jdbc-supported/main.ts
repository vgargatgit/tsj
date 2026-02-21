@Repository
class OrderRepository {
  countByStatus(status: string) {
    if (status == "OPEN") {
      return 2;
    }
    if (status == "CLOSED") {
      return 1;
    }
    return 0;
  }

  findById(id: number) {
    if (id == 101) {
      return "OPEN";
    }
    if (id == 102) {
      return "CLOSED";
    }
    return undefined;
  }
}

@Service
class OrderService {
  @Transactional
  reportOpenCount() {
    const repository = new OrderRepository();
    return repository.countByStatus("OPEN");
  }

  @Transactional
  lookupStatus(id: number) {
    const repository = new OrderRepository();
    const status = repository.findById(id);
    if (status == undefined) {
      throw "ORDER_NOT_FOUND";
    }
    return status;
  }
}
