package com.github.subhajitdas298.testpublishdataprotos.service;

import com.github.subhajitdas298.testpublishdataprotos.repository.DataRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProtoDataService {

    private final DataRepository dataRepository;

    public ProtoDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    @Cacheable("protoDataset")
    public byte[] getData() {
        return dataRepository.findData().toByteArray();
    }
}
