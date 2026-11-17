package com.aitracking.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aitracking.dto.BannerView;
import com.aitracking.security.AuthInterceptor;
import com.aitracking.service.BannerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public List<BannerView> list(
            @RequestAttribute(value = AuthInterceptor.USER_ID_ATTRIBUTE, required = false) String userId,
            @RequestParam(required = false) String game) {
        return bannerService.list(userId, game);
    }
}
