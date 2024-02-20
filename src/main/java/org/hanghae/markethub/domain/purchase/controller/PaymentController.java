package org.hanghae.markethub.domain.purchase.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.coyote.BadRequestException;
import org.hanghae.markethub.domain.item.service.ItemService;
import org.hanghae.markethub.domain.purchase.dto.IamportResponseDto;
import org.hanghae.markethub.domain.purchase.dto.PaymentRequestDto;
import org.hanghae.markethub.domain.purchase.dto.RefundRequestDto;
import org.hanghae.markethub.domain.purchase.service.PurchaseService;
import org.hanghae.markethub.global.jwt.JwtUtil;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import retrofit2.HttpException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class PaymentController {

    private final PurchaseService purchaseService;
    private final ItemService itemService;
    private final IamportClient iamportClient;
    private final RedissonClient redissonClient; // Redisson 클라이언트 주입
    private final JwtUtil jwtUtil;

    String secretKey = "KuT8n5XYtxPTo4c0VoRTQLrZeHJUOsx3h7zBXgrltDcL6yiH7KZ5ulZJVJWPeqRvPxfuE5B7u1G7Ioxc";
    String apiKey = "4067753427514612";

    @Autowired
    public PaymentController(PurchaseService purchaseService, ItemService itemService, RedissonClient redissonClient, JwtUtil jwtUtil) {
        this.itemService = itemService;
        this.purchaseService = purchaseService;
        this.redissonClient = redissonClient;
        this.jwtUtil = jwtUtil;
        this.iamportClient = new IamportClient(apiKey, secretKey);
    }


    @PostMapping("/verify")
    public IamportResponse<Payment> paymentByImpUid(@RequestBody PaymentRequestDto paymentRequestDto, HttpServletRequest req) throws IamportResponseException, IOException, InterruptedException {
        String email = jwtUtil.getUserEmail(req);
        RLock lock = redissonClient.getFairLock("payment:" + paymentRequestDto.impUid());
        try {
            // 락을 최대 10초 동안 대기하고, 락을 획득하면 최대 5초 동안 유지
            if (lock.tryLock(10, 5, TimeUnit.SECONDS)) {
                try {
                    System.out.println("Lock 획득 성공");
                    // 비즈니스 로직 처리
                    processPurchase(paymentRequestDto, email);
                    return iamportClient.paymentByImpUid(paymentRequestDto.impUid());
                } finally {
                    lock.unlock(); // 작업 완료 후 락 해제
                    System.out.println("lock 해제");
                }
            } else {
                throw new IllegalStateException("Unable to acquire lock for payment processing");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock acquisition interrupted", e);
        }
    }

    private Config getRedisConfig() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return config;
    }


    private void processPurchase(PaymentRequestDto paymentRequestDto, String email) throws IOException, InterruptedException {
        // DTO에서 impUid를 직접 참조
        String impUid = paymentRequestDto.impUid();

        for (PaymentRequestDto.PurchaseItemDto item : paymentRequestDto.items()) {
            if (itemService.isSoldOut(item.itemId())) {
                handleSoldOut(impUid, paymentRequestDto.amount());
            } else {
                try {
                    itemService.decreaseQuantity(item.itemId(), item.quantity()); // 구매한 수량만큼 재고 감소
                    purchaseService.updateImpUidForPurchases(email, impUid); // purchase 엔티티에 구매 id 저장
                } catch (Exception e) {
                    handleQuantityExceeded(impUid, paymentRequestDto.amount());
                }
            }
        }
        purchaseService.updatePurchaseStatusToOrdered(paymentRequestDto.email());
    }

    private void handleSoldOut(String impUid, double amount) throws IOException, InterruptedException {
        boolean cancelResult = cancelPayment(new RefundRequestDto(impUid, amount, "재고가 부족합니다."));
        System.out.println("환불 처리 결과(재고 부족): " + cancelResult);
        throw new BadRequestException("재고가 부족합니다");
    }

    private void handleQuantityExceeded(String impUid, double amount) throws IOException, InterruptedException {
        boolean cancelResult = cancelPayment(new RefundRequestDto(impUid, amount, "구매 수량이 재고보다 많습니다"));
        System.out.println("환불 처리 결과(구매수량보다 재고가 많아요): " + cancelResult);
        throw new IllegalArgumentException("상품의 재고가 부족합니다.");
    }

    @PostMapping("/api/payment/cancel")
    private boolean cancelPayment(@RequestBody RefundRequestDto refundRequestDto) throws IOException, InterruptedException {
        RestTemplate restTemplate = new RestTemplate();

        String url = "https://api.iamport.kr/payments/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = getAccessToken(new PaymentRequestDto.getToken("4067753427514612", "KuT8n5XYtxPTo4c0VoRTQLrZeHJUOsx3h7zBXgrltDcL6yiH7KZ5ulZJVJWPeqRvPxfuE5B7u1G7Ioxc")).join();
        // Authorization 헤더에 토큰을 추가합니다.
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<RefundRequestDto> request = new HttpEntity<>(refundRequestDto, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        System.out.println(response);
        if (response.getStatusCode() == HttpStatus.OK) {
            purchaseService.ChangeStatusToCancelled(refundRequestDto.imp_uid());
            return true;
        } else {
            return false;
        }
    }

    @PostMapping("/api/payment/token")
    public CompletableFuture<String> getAccessToken(@RequestBody PaymentRequestDto.getToken tokenData) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        String url = "https://api.iamport.kr/users/getToken";

        // tokenData를 JSON으로 직렬화
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper.writeValueAsString(tokenData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 비동기 요청을 보냅니다.
        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(HttpResponse::body) // 응답 본문을 가져옵니다.
                .thenApply(body -> {
                    try {
                        // 응답 본문을 IamportResponseDto 객체로 역직렬화
                        IamportResponseDto iamportResponseDto = objectMapper.readValue(body, IamportResponseDto.class);
                        System.out.println("토큰발급완료 Token : " + iamportResponseDto.response().access_token());
                        return iamportResponseDto.response().access_token();
                    } catch (Exception e) {
                        throw new RuntimeException("응답 본문 처리 중 오류 발생", e);
                    }
                });
    }

}
