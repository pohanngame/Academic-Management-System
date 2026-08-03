package com.example.academicprofile.access;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ResourceAccessRequestMapper extends BaseMapper<ResourceAccessRequest> {
    @Select("SELECT * FROM resource_access_request WHERE id = #{id} FOR UPDATE")
    ResourceAccessRequest selectByIdForUpdate(Long id);
}
