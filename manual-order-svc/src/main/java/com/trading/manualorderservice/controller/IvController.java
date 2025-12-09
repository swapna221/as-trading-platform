package com.trading.manualorderservice.controller;

import com.trading.manualorderservice.dto.IvRequestDto;
import com.trading.manualorderservice.dto.IvResponseDto;
import com.trading.manualorderservice.service.IvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manual-order")
@RequiredArgsConstructor
public class IvController {

    private final IvService ivService;

    @PostMapping("/iv")
    public IvResponseDto getOptionIv(@RequestBody IvRequestDto req) throws Exception {
        return ivService.fetchIv(req);
    }
}
