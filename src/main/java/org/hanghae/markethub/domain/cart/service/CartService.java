package org.hanghae.markethub.domain.cart.service;

import lombok.RequiredArgsConstructor;
import org.hanghae.markethub.domain.cart.config.CartConfig;
import org.hanghae.markethub.domain.cart.dto.CartRequestDto;
import org.hanghae.markethub.domain.cart.dto.CartResponseDto;
import org.hanghae.markethub.domain.cart.dto.UpdateValidResponseDto;
import org.hanghae.markethub.domain.cart.entity.Cart;
import org.hanghae.markethub.domain.cart.repository.CartRepository;
import org.hanghae.markethub.domain.item.entity.Item;
import org.hanghae.markethub.domain.item.service.ItemService;
import org.hanghae.markethub.domain.user.entity.User;
import org.hanghae.markethub.global.security.impl.UserDetailsImpl;
import org.hanghae.markethub.domain.user.service.UserService;
import org.hanghae.markethub.global.constant.Status;
import org.hanghae.markethub.global.service.AwsS3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartConfig cartConfig;
    private final AwsS3Service awsS3Service;
    private final UserService userService;
    private final ItemService itemService;
    private final CartRedisService cartRedisService;

    public ResponseEntity<String> addCart(User user, CartRequestDto requestDto){

        userService.checkUser(user.getId());

        Item item = itemService.getItemValid(requestDto.getItemId().get(0));

        cartConfig.validItem(item);

        Optional<Cart> checkCart = cartRepository.findByitemIdAndUser(item.getId(),user);

            if (checkCart.isPresent()) {

                cartConfig.changeCart(requestDto, item, checkCart);
            } else {
                Cart cart = Cart.builder()
                        .item(item)
                        .status(Status.EXIST)
                        .address(user.getAddress())
                        .quantity(requestDto.getQuantity().get(0))
                        .price(item.getPrice() * requestDto.getQuantity().get(0))
                        .user(user)
                        .build();

                cartRepository.save(cart);
            }

        return ResponseEntity.ok("Success Cart");
    }

//    @Transactional
//    public ResponseEntity<String> addNoUserCart(User user) throws UnknownHostException {
//
//        userService.checkUser(user.getId());
//
//        List<CartResponseDto> noUserCarts = cartRedisService.getAll();
//        if (noUserCarts.isEmpty()){
//            return ResponseEntity.ok("Success Cart");
//        }
//
//        for (CartResponseDto noUserCart : noUserCarts) {
//            Item item = itemService.getItemValid(noUserCart.getItem().getId());
//            cartConfig.validItem(item);
//
//            Optional<Cart> checkCart = cartRepository.findByitemIdAndUser(item.getId(),user);
//
//            if (checkCart.isPresent()) {
//
//                cartConfig.addNoUserCart(noUserCart, item, checkCart);
//            } else {
//                Cart cart = Cart.builder()
//                        .item(item)
//                        .status(Status.EXIST)
//                        .address(user.getAddress())
//                        .quantity(noUserCart.getQuantity())
//                        .price(noUserCart.getPrice())
//                        .user(user)
//                        .build();
//
//                cartRepository.save(cart);
//            }
//
//            cartRedisService.delete(noUserCart);
//        }
//
//        return ResponseEntity.ok("Success Cart");
//    }

@Transactional
public void addNoUserCart(User user) throws UnknownHostException {

    userService.checkUser(user.getId());

    List<CartResponseDto> noUserCarts = cartRedisService.getAll();
    if (noUserCarts.isEmpty()){

    }

    for (CartResponseDto noUserCart : noUserCarts) {
        Item item = itemService.getItemValid(noUserCart.getItem().getId());
        cartConfig.validItem(item);

        Optional<Cart> checkCart = cartRepository.findByitemIdAndUser(item.getId(),user);

        if (checkCart.isPresent()) {

            cartConfig.addNoUserCart(noUserCart, item, checkCart);
        } else {
            Cart cart = Cart.builder()
                    .item(item)
                    .status(Status.EXIST)
                    .address(user.getAddress())
                    .quantity(noUserCart.getQuantity())
                    .price(noUserCart.getPrice())
                    .user(user)
                    .build();

            cartRepository.save(cart);
        }

        cartRedisService.delete(noUserCart);
    }
}

    @Transactional
    public ResponseEntity<String> updateCart(User user, CartRequestDto requestDto,Long cartId) {

        userService.checkUser(user.getId());

        UpdateValidResponseDto valids = cartConfig.updateVaild(cartId);

        cartConfig.validItem(valids.getItem());

        valids.getCart().updateCart(requestDto,valids.getItem());

        return ResponseEntity.ok("ok");
    }


    @Transactional(readOnly = true)
    public List<CartResponseDto> getCarts(User user) throws NullPointerException{

        User validUser = userService.getUserEntity(user.getId());

            return cartRepository.findAllByUserAndStatusOrderByCreatedTime(validUser,Status.EXIST).stream()
                    .map(cart -> CartResponseDto.builder()
                            .id(String.valueOf(cart.getCartId()))
                            .price(cart.getPrice())
                            .item(itemService.getItemValid(cart.getItem().getId()))
                            .img(awsS3Service.getObjectUrlsForItem(cart.getItem().getId()).get(0))
                            .quantity(cart.getQuantity())
                            .build())
                    .collect(Collectors.toList());
    }

    @Transactional
    public ResponseEntity<String> deleteCart(User user,Long cartId){

        userService.checkUser(user.getId());

        Cart cart = cartRepository.findById(cartId).orElseThrow(null);
        cart.delete();

        return ResponseEntity.ok("ok");
    }

    @Transactional
    public ResponseEntity<String> deleteAllCart(UserDetailsImpl userDetails) {

        List<Cart> carts = cartRepository.findAllByUserAndStatusOrderByCreatedTime(userDetails.getUser(), Status.EXIST);

        for (Cart cart : carts) {
            cart.delete();
        }

        return ResponseEntity.ok("ok");

    }
}
