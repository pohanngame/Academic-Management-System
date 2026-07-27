package com.example.academicprofile.user;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    @Select("SELECT * FROM `user` WHERE id = #{id} FOR UPDATE")
    UserAccount selectByIdForUpdate(Long id);

    @Select("""
            SELECT * FROM `user`
            WHERE role = 'SUPER_ADMIN' AND status = 'ACTIVE'
            ORDER BY id
            FOR UPDATE
            """)
    List<UserAccount> selectActiveSuperAdminsForUpdate();
}
