package com.github.subhajitdas298.testpublishdataprotos.controller;

import com.github.subhajitdas298.testpublishdataprotos.service.DataGeneratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {

    private final DataGeneratorService dataGeneratorService;

    public DataController(DataGeneratorService dataGeneratorService) {
        this.dataGeneratorService = dataGeneratorService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public byte[] getData() {
        return dataGeneratorService.getData().toByteArray();
    }
}
