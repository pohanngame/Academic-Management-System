package com.example.academicprofile.user;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class UserAccountService {

    private final UserAccountMapper userAccountMapper;

    public UserAccountService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    public Optional<UserAccount> findByUsernameOrEmail(String usernameOrEmail) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, usernameOrEmail)
                .or()
                .eq(UserAccount::getEmail, usernameOrEmail);
        return Optional.ofNullable(userAccountMapper.selectOne(wrapper));
    }

    public boolean existsByUsername(String username) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)) > 0;
    }

    public boolean existsByEmail(String email) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email)) > 0;
    }

    public void create(UserAccount userAccount) {
        userAccountMapper.insert(userAccount);
    }

    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(userAccountMapper.selectById(id));
    }
}
