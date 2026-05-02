package com.mint.Mint.Automation.Slave.repository;

import com.mint.Mint.Automation.Slave.entity.DeviceEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeviceRepository extends MongoRepository<DeviceEntity, ObjectId> {
}
