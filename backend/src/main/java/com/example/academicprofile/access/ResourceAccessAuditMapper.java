package com.example.academicprofile.access;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ResourceAccessAuditMapper extends BaseMapper<ResourceAccessAudit> {
}
