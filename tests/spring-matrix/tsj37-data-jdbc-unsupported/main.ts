@Repository
class OrderRepository {
  @Query("select * from orders")
  findAll() {
    return [];
  }
}
