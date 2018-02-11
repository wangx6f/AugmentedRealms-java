package io.augmentedrealms.client.service;

import io.augmentedrealms.client.exception.ApiException;
import io.augmentedrealms.client.exception.ApiExceptionResponse;
import io.augmentedrealms.client.model.UserCredential;
import io.augmentedrealms.client.model.UserDetailRecord;
import io.augmentedrealms.client.model.UserRecord;
import io.augmentedrealms.client.model.factory.UserDetailRecordFactory;
import io.augmentedrealms.client.security.JWTokenAuth;
import io.augmentedrealms.common.database.model.User;
import io.augmentedrealms.common.database.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private UserRepository userRepository;

    private PasswordEncoder encoder;

    private UserDetailRecordFactory userDetailRecordFactory;

    @Value("${jwt.secret}")
    private  String secretKey;

    private final long EXPIRE_TIME_IN_MILLIS = TimeUnit.DAYS.toMillis(48);

    public UserService(UserRepository userRepository, PasswordEncoder encoder,
                       UserDetailRecordFactory userDetailRecordFactory) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.userDetailRecordFactory = userDetailRecordFactory;
    }

    public void userRegister(UserRecord newUser) throws ApiException {
        if (userRepository.existsByEmail(newUser.getEmail())) {
            throw new ApiException(ApiExceptionResponse.DUPLICATE_USER);
        }
        newUser.setPassword(encoder.encode(newUser.getPassword()));
        userRepository.save(newUser.convertTo());
    }

    public String userLogin(UserCredential userCredential) throws ApiException {
        User user = getUserFromRepo(userCredential.getEmail());
        if (!encoder.matches(userCredential.getPassword(), user.getPassword())) {
            throw new ApiException(ApiExceptionResponse.PASSWORD_INCORRECT);
        } else {
            // issue and update the token for the user
            String token = issueToken(user);
            user.setToken(token);
            userRepository.save(user);
            return token;
        }
    }


    public void userLogOut(User user){
        user.setToken(null);
        userRepository.save(user);
    }


    public UserDetailRecord getUserDetail(User user){
        return userDetailRecordFactory.convertFrom(user);
    }

    private String issueToken(User user) {
        return Jwts.builder().setSubject(user.getEmail()).signWith(SignatureAlgorithm.HS512, secretKey).
                setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME_IN_MILLIS)).compact();

    }

    public User getUserFromHeader() throws ApiException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JWTokenAuth) {
            User user =  getUserFromRepo((String) authentication.getPrincipal());
            if (user.getToken()!=null && user.getToken().equals(authentication.getCredentials())){
                return user;
            }
        }
        throw new ApiException(ApiExceptionResponse.TOKEN_INVALID);
    }

    @NotNull
    private User getUserFromRepo(String email) throws ApiException{

        Optional<User> user = userRepository.findByEmail(email);
        if(!user.isPresent()){
            throw new ApiException(ApiExceptionResponse.USER_NOT_FOUND);
        } else {
            return user.get();
        }
    }

}
