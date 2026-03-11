package com.example.shop.controller.dto;

public record TicketResponse(
        Ticket ticket,
        String signature
) {
}