package com.staticnur.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staticnur.model.Address;
import com.staticnur.services.AddressFormation;
import com.staticnur.services.EsService;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class EsServiceImpl implements EsService {

    private final static String INDEX_NAME = "address";
    private final RestHighLevelClient esClient;
    private final ObjectMapper mapper;
    private final AddressFormation addressFormation;

    @Autowired
    public EsServiceImpl(RestHighLevelClient esClient, ObjectMapper mapper, AddressFormation addressFormation) {
        this.esClient = esClient;
        this.mapper = mapper;
        this.addressFormation = addressFormation;
    }

    @Override
    public List<Address> searchAddress(String query) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        query = query.toLowerCase()
                .replaceFirst("республика", "респ")
                .replaceAll("[,;!&$?№~@#%^*+:<>=]", "")
                .replaceAll("[-.]"," ")
                .replaceAll("/"," ");
        String[] addressParts = query.split(" ");

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        String houseNumber = null;
        String apartmentNumber = null;
        for (String part : addressParts) {
            System.out.println(part);
            if (part.matches("\\d+")) {
                if (houseNumber == null) {
                    houseNumber = part;
                } else {
                    apartmentNumber = part;
                }
            }
            boolQuery.must(QueryBuilders.regexpQuery("address", "(.*?)(" + part + ")(.*?)"));
        }
        if (houseNumber != null) {
            boolQuery.should(QueryBuilders.termQuery("address", houseNumber).boost(2));
        }
        if (apartmentNumber != null) {
            boolQuery.should(QueryBuilders.termQuery("address", apartmentNumber).boost(1));
        }

        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(10000);
        searchSourceBuilder.sort("_score", SortOrder.ASC);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse;
        try {
            searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return extractAddressesFromResponse(searchResponse);
    }

    @Override
    public void download() {
        long batchSize = 10_000;
        long fromIndex = 0;
        long toIndex = batchSize;

        List<Address> addressBatch = addressFormation.getAddress(fromIndex, toIndex);

        for (; fromIndex < addressBatch.size(); fromIndex += batchSize) {
            toIndex = Math.min(fromIndex + batchSize, addressBatch.size());

            List<Address> batch = addressBatch.subList((int) fromIndex, (int) toIndex);

            BulkRequest bulkRequest = prepareBulkRequest(batch);
            executeBulkRequest(bulkRequest);
        }
        toIndex += batchSize;
        System.out.println("Size: " + toIndex);//Size: 552_257
    }

    private List<Address> extractAddressesFromResponse(SearchResponse searchResponse) {
        List<Address> addresses = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Address address = getAddress(hit);
            addresses.add(address);
        }
        Collections.reverse(addresses);
        return addresses;
    }

    private Address getAddress(SearchHit hit) {
        Map<String, Object> sourceAsMap = hit.getSourceAsMap();
        return Address.builder()
                .address(sourceAsMap.getOrDefault("address", "").toString())
                .path(sourceAsMap.getOrDefault("path", "").toString())
                .build();
    }

    private BulkRequest prepareBulkRequest(List<Address> allAddress) {
        BulkRequest bulkRequest = new BulkRequest();
        for (Address address : allAddress) {
            IndexRequest indexRequest = new IndexRequest(INDEX_NAME);
            indexRequest.id(UUID.randomUUID().toString());
            try {
                indexRequest.source(mapper.writeValueAsString(address), XContentType.JSON);
                bulkRequest.add(indexRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return bulkRequest;
    }

    private void executeBulkRequest(BulkRequest bulkRequest) {
        try {
            BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            handleBulkResponse(bulkResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleBulkResponse(BulkResponse bulkResponse) {
        if (bulkResponse.hasFailures()) {
            for (BulkItemResponse bulkItemResponse : bulkResponse) {
                if (bulkItemResponse.isFailed()) {
                    BulkItemResponse.Failure failure = bulkItemResponse.getFailure();
                    throw new RuntimeException("Failed to process request: " + failure.getMessage());
                }
            }
        }
    }
}
