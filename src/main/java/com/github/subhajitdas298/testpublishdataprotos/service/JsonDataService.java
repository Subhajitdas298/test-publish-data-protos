package com.github.subhajitdas298.testpublishdataprotos.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotos.repository.DataRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class JsonDataService {

    private final DataRepository dataRepository;

    public JsonDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    @Cacheable("jsonDataset")
    public String getData() throws InvalidProtocolBufferException {
        Root root = dataRepository.findData();
        return JsonFormat.printer().print(root);
    }
}
