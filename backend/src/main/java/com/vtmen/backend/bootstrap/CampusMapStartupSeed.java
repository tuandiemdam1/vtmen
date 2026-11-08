package com.vtmen.backend.bootstrap;

import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.service.CampusMapService;
import com.vtmen.backend.service.DcsDestinationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

 // After Mongo is up: fill {@code maps} from catalog / DCS when empty, then refresh {@link DcsDestinationRegistry}.
@Component
@Order(0)
public class CampusMapStartupSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CampusMapStartupSeed.class);

    @Autowired
    private CampusMapService campusMapService;

    @Autowired
    private DcsDestinationRegistry dcsDestinationRegistry;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        campusMapService.seedMapsIfDatabaseEmpty();
        dcsDestinationRegistry.reloadFromMongo();
        try {
            Query q = new Query(Criteria.where("status").in("delivered", "cancelled").and("compartmentId").ne(null));
            Update u = new Update().unset("compartmentId");
            mongoTemplate.updateMulti(q, u, OrderModel.class);
        } catch (Exception e) {
            log.warn("Could not clean legacy compartment IDs: {}", e.getMessage());
        }
        log.info("Campus map seed check complete (registry reloaded from Mongo)");
    }
}
