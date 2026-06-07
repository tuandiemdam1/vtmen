package com.vtmen.backend.repository;

import com.vtmen.backend.model.OrderModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<OrderModel, String> {
    List<OrderModel> findByStatus(String status);

    List<OrderModel> findByStatusNot(String status);
    
    List<OrderModel> findByCompartmentId(Integer compartmentId);
    
    java.util.Optional<OrderModel> findByOrderCode(String orderCode);

    java.util.Optional<OrderModel> findByPinCode(String pinCode);
}
