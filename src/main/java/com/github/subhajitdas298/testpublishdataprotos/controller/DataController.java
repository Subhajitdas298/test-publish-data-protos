package com.github.subhajitdas298.testpublishdataprotos.controller;

import com.github.subhajitdas298.testpublishdataprotos.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotos.service.ProtoDataService;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<byte[]> getProtoData() {
        return noStore(protoDataService.getData());
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJsonData() throws InvalidProtocolBufferException {
        return noStore(jsonDataService.getData());
    }

    // No intermediary (browser, proxy, CDN) may cache this response — every request must
    // reach this service.
    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}
