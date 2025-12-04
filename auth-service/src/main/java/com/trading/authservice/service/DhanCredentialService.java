package com.trading.authservice.service;


import com.trading.authservice.dtos.DhanCredentialRequest;
import com.trading.authservice.entity.DhanCredential;
import com.trading.authservice.entity.User;
import com.trading.authservice.repository.DhanCredentialRepository;
import com.trading.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DhanCredentialService {

 private final DhanCredentialRepository dhanCredentialRepository;
 private final UserRepository userRepository;

 public void saveCredentials(String username, DhanCredentialRequest request) {
     User user = userRepository.findByUsername(username)
             .orElseThrow(() -> new RuntimeException("User not found"));

     DhanCredential credential = dhanCredentialRepository.findByUserId(user.getId())
             .orElse(new DhanCredential());

     credential.setUser(user);
     credential.setAccessToken(request.accessToken());
     credential.setClientId(request.clientId());

     dhanCredentialRepository.save(credential);
 }
}

