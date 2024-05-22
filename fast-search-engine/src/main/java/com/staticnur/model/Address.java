package com.staticnur.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Address {
    private String address;
    private String path;
}
