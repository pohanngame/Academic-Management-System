package com.example.academicprofile.user;

import java.util.Optional;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public void update(UserAccount userAccount) {
        userAccountMapper.updateById(userAccount);
    }

    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(userAccountMapper.selectById(id));
    }

    public Optional<UserAccount> findByIdForUpdate(Long id) {
        return Optional.ofNullable(userAccountMapper.selectByIdForUpdate(id));
    }

    public List<UserAccount> lockActiveSuperAdmins() {
        return userAccountMapper.selectActiveSuperAdminsForUpdate();
    }

    public long countBy(String role, String status) {
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        if (role != null) {
            wrapper.eq("role", role);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        return userAccountMapper.selectCount(wrapper);
    }

    public Page<UserAccount> page(long page, long size, String search, String role, String status) {
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        if (org.springframework.util.StringUtils.hasText(search)) {
            String term = search.trim();
            wrapper.and(query -> query.like("username", term)
                    .or().like("email", term)
                    .or().apply(
                            "EXISTS (SELECT 1 FROM teacher_profile tp "
                                    + "WHERE tp.user_id = user.id AND tp.display_name LIKE CONCAT('%', {0}, '%'))",
                            term));
        }
        if (org.springframework.util.StringUtils.hasText(role)) {
            wrapper.eq("role", role);
        }
        if (org.springframework.util.StringUtils.hasText(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        return userAccountMapper.selectPage(Page.of(page, size), wrapper);
    }

}
