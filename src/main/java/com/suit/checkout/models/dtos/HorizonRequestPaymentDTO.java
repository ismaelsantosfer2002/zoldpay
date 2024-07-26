package com.suit.checkout.models.dtos;

import com.suit.checkout.models.dtos.wegate.PixObjectData;

import java.util.List;

public record HorizonRequestPaymentDTO(
        Integer amount,
        String paymentMethod,
        ClientRequestHorizon customer,
        ShippingData shipping,
        List<ItemsHorizonDTO> items,
        PixObjectData pix,
       // String externalRef,
        String postbackUrl,
        Boolean traceable




) {
}
