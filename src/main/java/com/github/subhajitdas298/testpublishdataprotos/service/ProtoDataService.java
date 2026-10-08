package com.github.subhajitdas298.testpublishdataprotos.service;

import com.github.subhajitdas298.testpublishdataprotos.repository.DataRepository;
import org.springframework.stereotype.Service;

@Service
public class ProtoDataService {

    private final DataRepository dataRepository;

    public ProtoDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    // The bundled files already are the wire format, so they are served as they are.
    public byte[] getData(int size) {
        return dataRepository.findData(size);
    }
}
