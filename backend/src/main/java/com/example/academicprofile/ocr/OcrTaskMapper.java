package com.example.academicprofile.ocr;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface OcrTaskMapper extends BaseMapper<OcrTask> {
    @Select("SELECT * FROM ocr_task WHERE id=#{id} FOR UPDATE")
    OcrTask selectByIdForUpdate(Long id);
}
