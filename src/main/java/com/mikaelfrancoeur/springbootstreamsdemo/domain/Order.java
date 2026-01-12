package com.mikaelfrancoeur.springbootstreamsdemo.domain;

public record Order(String id, String customerId, double amount, String status) {}
