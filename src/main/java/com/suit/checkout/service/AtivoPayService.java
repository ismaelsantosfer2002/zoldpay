package com.suit.checkout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import com.suit.checkout.models.Pagamentos;
import com.suit.checkout.models.dtos.*;
import com.suit.checkout.models.dtos.wegate.DataPix;
import com.suit.checkout.models.dtos.wegate.PixObjectData;
import com.suit.checkout.models.dtos.wegate.WeGateLogin;
import com.suit.checkout.models.dtos.wl.DataCallbackDTO;
import com.suit.checkout.models.dtos.wl.ResponseData;
import com.suit.checkout.models.dtos.wl.WhiteLabelCallBackDTO;
import com.suit.checkout.models.enums.StatusPagamento;
import com.suit.checkout.repositories.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class AtivoPayService {

    @Autowired
    private PaymentService paymentService;

    private static final String SecretKey = "sk_5971264e7f40a4a7e4c16e7b41d3a853d722b9beaac6ab640929bf7140188cb376c3827ccc056cd9";
    private static final String callbackUrl = "https://rodapremios.com/api/payment/callback";
    private static final String postUrlHorizon = "https://api.kingcash.app/v1/transactions";
    private static final String publicKeyWeGate = "pk_3e22dd47188d4b14aa1926cb8d2df7df7e868fce2a619de410225b50ceb522bb";
    private static final String secretKeyWegate = "sk_5971264e7f40a4a7e4c16e7b41d3a853d722b9beaac6ab640929bf7140188cb376c3827ccc056cd9";
    private static final String urlLoginWegate = "https://api.wegate.com.br/v1/auth/login";
    private static final String urlZoldPay = "https://api.zoldpay.com.br/api/user/transactions";
    private static final String secretKeyZoldPay = "192e867b-5e28-47a7-ab3f-afe0ce2f4338";


    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private GenerateQRCode generateQRCode;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    public String loginInWeGate(){

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        WeGateLogin weGateLogin = new WeGateLogin(publicKeyWeGate, secretKeyWegate);

        String json;
        try {
            json = new ObjectMapper().writeValueAsString(weGateLogin);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao converter para JSON.");
        }

        System.out.println(json);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(urlLoginWegate, HttpMethod.POST, entity, String.class);
        System.out.println(responseEntity);
        return responseEntity.getBody();
    }

    public Object createPaymentHorizon(RequestApiPaymentDTO data) throws IOException, WriterException {
        Pagamentos pagamentoModel = paymentService.createPayment(data);

        Integer valorApagar = (int) (data.valorAPagar() * 100);
        ItemsHorizonDTO itemsHorizonDTO = new ItemsHorizonDTO("TENIS AZUL", valorApagar, 1, false);
        HorizonDocumentRequestDTO horizonDocumentRequestDTO = new HorizonDocumentRequestDTO(data.cpf(), "CPF");
        ClientRequestHorizon clientRequestHorizon = new ClientRequestHorizon(data.nomePagador(), data.email(), horizonDocumentRequestDTO,"88998678372", "31232131");
        AddressRequestDTO addressRequestDTO = new AddressRequestDTO("rua 18", "133", "casa", "14790000", "centro", "guaira", "SP", "br");
        PixObjectData pixObjectData = new PixObjectData(1);
        ShippingData shippingData = new ShippingData(1, addressRequestDTO);
        HorizonRequestPaymentDTO horizonRequestPaymentDTO = new HorizonRequestPaymentDTO(valorApagar, "PIX", clientRequestHorizon, shippingData, List.of(itemsHorizonDTO), pixObjectData, callbackUrl, false);

        //String auth = loginInWeGate();

        String auth = "Basic " + Base64.getEncoder().encodeToString((secretKeyZoldPay).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.set("Content-Type", "application/json");



        String json;
        try {
            json = new ObjectMapper().writeValueAsString(horizonRequestPaymentDTO);
            System.out.println(json);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao converter para JSON.");
        }

        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        ResponseEntity<DataPix> responseEntity = restTemplate.exchange(urlZoldPay, HttpMethod.POST, entity, DataPix.class);

        DataPix responseData = responseEntity.getBody();
        pagamentoModel.setIdTransactionWegate(responseData.data().id());
        pagamentoRepository.save(pagamentoModel);
        String codeBase64 = generateQRCode.generateQRCodeBase64(responseData.data().pix().qrcode());
        ResponsePIX responsePIX = new ResponsePIX(codeBase64, responseData.data().pix().qrcode(), "Pagamento com Wegate");


        return responsePIX;
    }

    public void callback(WhiteLabelCallBackDTO data) {
        Pagamentos pagamento = pagamentoRepository.findPagamentosByIdTransactionWegate(data.data().id());
        if (pagamento == null) {
            throw new RuntimeException("Pagamento não encontrado");
        }
        System.out.println(data.data().status());
        if(data.data().status().equals("paid")) {
            pagamento.setStatusPagamento(StatusPagamento.PAGO);
            pagamento.setDataPagamento(LocalDateTime.now());
            pagamentoRepository.save(pagamento);
        }
        if (data.data().status().equals("expired")) {
            pagamento.setStatusPagamento(StatusPagamento.CANCELADO);
            pagamentoRepository.save(pagamento);
        }
    }
}
