package com.finovago.p2p.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finovago.p2p.service.GiftCardService;


@RestController
@RequestMapping("/redeem")
@CrossOrigin(origins = "http://localhost:3000")
public class GiftCardController
{
    private final GiftCardService giftCardService;

    public GiftCardController(GiftCardService giftCardService) {
        this.giftCardService = giftCardService;
    }
    
    @PostMapping("/giftcard/{giftCardCode}")
    public String redeemGiftCard(@PathVariable String giftCardCode,@RequestParam double amount)
    {
        return "Amount to pay after applying gift card: " + giftCardService.redeemGiftCard(giftCardCode, amount);
    }

    @PostMapping("/giftcard/create")
    @ResponseStatus(HttpStatus.CREATED)
    public String createGiftCard(@RequestParam String giftCardCode,@RequestParam double balance, @RequestParam boolean active)
    {
        giftCardService.createGiftCard(giftCardCode, balance, active);
        return "Gift card created successfully";
    }
}
