package com.github.subhajitdas298.testpublishdataprotos.controller;

import com.github.subhajitdas298.testpublishdataprotos.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotos.service.ProtoDataService;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public byte[] getProtoData() {
        return protoDataService.getData();
    }

    @GetMapping(value = "/api/data/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getJsonData() throws InvalidProtocolBufferException {
        return jsonDataService.getData();
    }
}
