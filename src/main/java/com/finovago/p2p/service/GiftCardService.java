package com.finovago.p2p.service;
import org.springframework.stereotype.Service;

import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.repository.GiftCardRepository;  

@Service
public class GiftCardService {
    private GiftCardRepository giftCardRepository;

    public GiftCardService(GiftCardRepository giftCardRepository) {
        this.giftCardRepository = giftCardRepository;
    }

    public double redeemGiftCard(String cardCode, double amount)
    {
        if(cardCode == null || cardCode.isEmpty()) throw new IllegalArgumentException("Card code cannot be null or empty");
        if(amount <= 0) throw new IllegalArgumentException("Amount must be greater than zero");

        GiftCard giftCard = giftCardRepository.findByCardCode(cardCode).orElseThrow(() -> new UnknownGiftCardException("Gift card with code " + cardCode + " not found"));

        if(!giftCard.isActive()) throw new InactiveGiftCardException("Gift card with code " + cardCode + " is inactive");

        double remainingToPay;

        if (giftCard.getBalance() > amount) {
            giftCard.deductBalance(amount);
            remainingToPay = 0.0;
        } else {
            amount = amount - giftCard.getBalance();
            giftCard.drainCard();
            remainingToPay = amount;
        }

        giftCardRepository.save(giftCard);
        return remainingToPay;
    }

    public void createGiftCard(String cardCode, double balance, boolean active)
    {
        if(cardCode == null || cardCode.isEmpty()) throw new IllegalArgumentException("Card code cannot be null or empty");
        if(balance < 0) throw new IllegalArgumentException("Balance cannot be negative");

        GiftCard giftCard = new GiftCard(cardCode, balance, active);
        giftCardRepository.save(giftCard);
    }
}
